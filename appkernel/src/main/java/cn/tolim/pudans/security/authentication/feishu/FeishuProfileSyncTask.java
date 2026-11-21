package cn.tolim.pudans.security.authentication.feishu;

import cn.tolim.pudans.core.user.FeishuAccounts;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
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

    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(60);

    private final SecurityParameters securitySettings;

    private final FeishuClient feishuClient;

    private final FeishuAccounts feishuAccounts;

    private Disposable subscription;

    private volatile Instant lastSyncAt = Instant.EPOCH;

    @PostConstruct
    public void start() {
        subscription = Flux.concat(Mono.just(0L), Flux.interval(CHECK_INTERVAL, CHECK_INTERVAL))
                .onBackpressureDrop()
                .concatMap(ignored -> syncIfDue())
                .onErrorContinue((error, ignored) -> log.error("飞书资料同步失败", error))
                .subscribe();
    }

    Mono<Void> syncIfDue() {
        var settings = securitySettings.feishu();
        if (!settings.isConfigured() || settings.syncIntervalSeconds() <= 0) {
            return Mono.empty();
        }
        var now = Instant.now();
        if (now.isBefore(lastSyncAt.plusSeconds(settings.syncIntervalSeconds()))) {
            return Mono.empty();
        }
        lastSyncAt = now;
        return syncAll();
    }

    Mono<Void> syncAll() {
        return feishuAccounts
                .listAllConnections()
                .concatMap(connection -> feishuClient
                        .fetchContactUser(connection.getSpec().getProviderUserId())
                        .flatMap(userInfo -> feishuAccounts.syncProfile(
                                connection.getSpec().getUsername(),
                                userInfo.name(),
                                userInfo.email(),
                                userInfo.phone(),
                                userInfo.avatar()))
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
