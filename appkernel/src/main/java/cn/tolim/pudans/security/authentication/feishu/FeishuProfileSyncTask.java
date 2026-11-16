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
                .onErrorContinue((error, ignored) -> log.error("飞书资料同步失败", error))
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
                                    "无法同步用户 {} 的飞书资料",
                                    connection.getSpec().getUsername(),
                                    error);
                            return Mono.empty();
                        }))
                .then()
                .doOnSuccess(ignored -> log.info("飞书资料同步完成"))
                .onErrorResume(error -> {
                    log.warn("飞书资料同步已跳过：{}", error.getMessage());
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
