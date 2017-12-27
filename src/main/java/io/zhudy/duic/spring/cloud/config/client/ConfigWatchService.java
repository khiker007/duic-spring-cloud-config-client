package io.zhudy.duic.spring.cloud.config.client;

import io.zhudy.duic.spring.cloud.config.environment.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kevin Zou (kevinz@weghst.com)
 */
public class ConfigWatchService implements Closeable {

    private static Logger log = LoggerFactory.getLogger(ConfigWatchService.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ContextRefresher refresher;
    private Environment environment;
    private ConfigClientProperties properties;

    public ConfigWatchService(ContextRefresher refresher, Environment environment,
                              ConfigClientProperties clientProperties) {
        this.refresher = refresher;
        this.environment = environment;
        this.properties = clientProperties;
    }

    @PostConstruct
    public void start() {
        this.running.compareAndSet(false, true);
    }

    @Scheduled(initialDelayString = "${duic.spring.cloud.config.watch.initialDelay:60000}",
            fixedDelayString = "${duic.spring.cloud.config.watch.fixedDelay:60000}")
    public void watch() {
        if (this.running.get()) {
            String state = environment.getProperty("config.client.state");
            String remoteState = getRemoteState();

            if (remoteState != null && !remoteState.equals(state)) {
                log.info("Reloading config: name={}, profiles={}, state={}, remoteState={}", properties.getName(),
                        properties.getProfile(), state, remoteState);
                refresher.refresh();
            }
        }
    }

    private String getRemoteState() {
        String path = "/apps/states/{name}/{profile}";
        String name = properties.getName();
        String profile = properties.getProfile();
        String uri = properties.getUri();

        log.debug("Checking config state from server at: {}", properties.getUri());

        Object[] args = new String[]{name, profile};
        ResponseEntity<State> response = null;
        try {
            RestTemplate restTemplate = RestTemplateUtils.getRestTemplate(properties);
            response = restTemplate.exchange(uri + path, HttpMethod.GET, null, State.class, args);
        } catch (Exception e) {
            log.warn("Checking config state failed: {}", e.getMessage());
        }

        if (response == null || response.getStatusCode() != HttpStatus.OK) {
            return null;
        }
        State result = response.getBody();
        return result.getState();
    }

    @Override
    public void close() throws IOException {
        running.compareAndSet(true, false);
    }
}