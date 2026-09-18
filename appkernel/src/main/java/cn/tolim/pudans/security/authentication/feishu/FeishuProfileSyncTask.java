package cn.tolim.pudans.security.authentication.feishu;

import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuProfileSyncTask {

    private final FeishuProperties properties;

    private final FeishuClient feishuClient;

    private final FeishuUserService feishuUserService;

    private Disposable subscription;

    @PostConstruct
    public void start() {
        if (!properties.isEnabled() || properties.getSyncIntervalSeconds() <= 0) {
            return;
        }
        var interval = Duration.ofSeconds(properties.getSyncIntervalSeconds());
        subscription = Flux.interval(interval, interval)
                .onBackpressureDrop()
                .concatMap(ignored -> syncAll())
                .onErrorContinue((error, ignored) -> log.error("Feishu profile synchronization failed", error))
                .subscribe();
    }

    Mono<Void> syncAll() {
        return feishuUserService
                .listAllConnections()
                .concatMap(connection -> feishuClient
                        .fetchContactUser(connection.getSpec().getProviderUserId())
                        .flatMap(userInfo -> feishuUserService.syncProfile(
                                connection.getSpec().getUsername(), userInfo.name(), userInfo.email()))
                        .onErrorResume(error -> {
                            log.warn(
                                    "Unable to sync Feishu profile for user {}",
                                    connection.getSpec().getUsername(),
                                    error);
                            return Mono.empty();
                        }))
                .then()
                .doOnSuccess(ignored -> log.info("Feishu profile synchronization finished"))
                .onErrorResume(error -> {
                    log.warn("Feishu profile synchronization skipped: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
