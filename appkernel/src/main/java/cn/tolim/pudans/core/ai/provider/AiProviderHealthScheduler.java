package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.balance.ProviderBalanceRegistry;
import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderHealthScheduler implements SmartLifecycle {

    private static final Duration PROBE_INTERVAL = Duration.ofMinutes(30);

    private final ReactiveExtensionClient client;

    private final ProviderBalanceRegistry balanceRegistry;

    private volatile boolean running;

    private Disposable probe;

    Mono<Long> sweepOnce() {
        return client.listAll(AiProviderAccount.class, ListOptions.builder().build(), defaultSort())
                .filter(AiProviderHealthScheduler::eligible)
                .concatMap(account -> probe(account).thenReturn(1L))
                .count();
    }

    private Mono<Void> probe(AiProviderAccount account) {
        var providerName = account.getSpec().getProvider();
        return client.get(AiProvider.class, providerName)
                .flatMap(provider -> balanceRegistry.query(provider, account))
                .flatMap(result -> {
                    updateBalance(account, result.left(), result.unit());
                    updateStatus(account, 1.0, null);
                    return client.update(account);
                })
                .then()
                .onErrorResume(error -> {
                    if (error instanceof ApiException api
                            && AiProviderBalanceProbeClient.CODE_NOT_CONFIGURED.equals(api.getCode())) {
                        return Mono.empty();
                    }
                    log.warn("供应商账号 {} 余额探测失败：{}", account.getMetadata().getName(), errorMessage(error));
                    updateStatus(account, 0.0, errorMessage(error));
                    return client.update(account).then();
                });
    }

    private static void updateBalance(AiProviderAccount account, BigDecimal left, String unit) {
        var balance = account.getSpec().getBalance() == null
                ? new AiProviderAccount.Balance()
                : account.getSpec().getBalance();
        balance.setLeft(left);
        if (StringUtils.hasText(unit)) {
            try {
                balance.setUnit(AiProviderAccount.QuotaUnit.valueOf(unit));
            } catch (IllegalArgumentException error) {
                log.debug("未知余额单位：{}。", unit);
            }
        }
        account.getSpec().setBalance(balance);
    }

    private static void updateStatus(
            AiProviderAccount account, double healthScore, String error) {
        var status = account.getStatus() == null
                ? new AiProviderAccount.Status()
                : account.getStatus();
        status.setHealthScore(healthScore);
        status.setLastError(error);
        account.setStatus(status);
    }

    private static boolean eligible(AiProviderAccount account) {
        return account != null
                && account.getSpec() != null
                && account.getSpec().isEnabled()
                && StringUtils.hasText(account.getSpec().getProvider());
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return StringUtils.hasText(message) ? message : error.getClass().getSimpleName();
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        probe = Flux.interval(PROBE_INTERVAL)
                .concatMap(tick -> sweepOnce()
                        .onErrorResume(error -> {
                            log.warn("供应商健康探测失败。", error);
                            return Mono.empty();
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.error("供应商健康探测调度异常退出。", error));
    }

    @Override
    public void stop() {
        running = false;
        if (probe != null) {
            probe.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}