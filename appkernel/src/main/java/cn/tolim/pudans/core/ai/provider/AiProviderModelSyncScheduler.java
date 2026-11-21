package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 定时把启用供应商的模型清单同步到本地，失败只记录日志
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderModelSyncScheduler implements SmartLifecycle {

    private static final Duration SYNC_INTERVAL = Duration.ofHours(6);

    private final ReactiveExtensionClient client;

    private final ProviderModelSync providerModelSync;

    private volatile boolean running;

    private Disposable sync;

    Mono<Long> syncOnce() {
        return client.listAll(AiProvider.class, ListOptions.builder().build(), defaultSort())
                .filter(AiProviderModelSyncScheduler::eligible)
                .concatMap(provider -> sync(provider.getMetadata().getName()))
                .count();
    }

    private Mono<Long> sync(String providerName) {
        return providerModelSync
                .sync(providerName)
                .doOnNext(result -> log.debug(
                        "供应商 {} 模型同步完成：新增 {}，更新 {}，未变 {}。",
                        providerName,
                        result.added(),
                        result.updated(),
                        result.unchanged()))
                .thenReturn(1L)
                .onErrorResume(error -> {
                    if (error instanceof ApiException api
                            && (ProviderModelSync.CODE_NO_ACCOUNT.equals(api.getCode())
                                    || ProviderModelSync.CODE_UNSUPPORTED.equals(api.getCode()))) {
                        log.debug("跳过供应商 {} 的模型同步：{}", providerName, api.getReason());
                        return Mono.just(0L);
                    }
                    log.warn("供应商 {} 模型同步失败：{}", providerName, errorMessage(error));
                    return Mono.just(0L);
                });
    }

    private static boolean eligible(AiProvider provider) {
        return provider != null
                && provider.getSpec() != null
                && provider.getSpec().isEnabled()
                && provider.getMetadata() != null
                && provider.getMetadata().getName() != null;
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        sync = Flux.interval(SYNC_INTERVAL)
                .concatMap(tick -> syncOnce().onErrorResume(error -> {
                    log.warn("供应商模型同步调度失败。", error);
                    return Mono.empty();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.error("供应商模型同步调度异常退出。", error));
    }

    @Override
    public void stop() {
        running = false;
        if (sync != null) {
            sync.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}