package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STORAGE_POLICY_INVALID;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STORAGE_POLICY_MISSING;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.Policies;
import cn.tolim.pudans.core.infra.exception.ApiException;
import reactor.core.publisher.Mono;

// 解析录制可用的对象存储策略，本地存储永远不作为录制落盘目标
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStoragePolicyResolver {

    private final Policies policies;

    // 指定策略名为空时取默认策略，未配置默认策略抛 LIVE_STORAGE_POLICY_MISSING
    public Mono<StoragePolicy> resolve(String storagePolicyName) {
        if (!StringUtils.hasText(storagePolicyName)) {
            return policies
                    .defaultPolicy()
                    .onErrorResume(error -> {
                        log.warn("录制未找到可用的默认存储策略：{}", error.getMessage());
                        return Mono.error(LIVE_STORAGE_POLICY_MISSING.exception());
                    })
                    .flatMap(this::requireRecordable);
        }
        return policies
                .get(storagePolicyName)
                .onErrorResume(error -> {
                    if (isNotFound(error)) {
                        log.warn("录制指定的存储策略不存在 [{}]", storagePolicyName);
                        return Mono.error(LIVE_STORAGE_POLICY_INVALID.exception());
                    }
                    return Mono.error(error);
                })
                .flatMap(this::requireRecordable);
    }

    public Mono<StoragePolicy> resolveForRecording(LiveRecording recording) {
        return Mono.defer(() -> resolve(recording.getSpec().getStoragePolicy()));
    }

    // 上传前复用同一判定，禁止把本地策略当作对象存储
    public static boolean isRecordable(StoragePolicy policy) {
        if (policy == null) {
            return false;
        }
        var spec = policy.getSpec();
        var status = policy.getStatus();
        return spec != null && spec.getType() != null
                && spec.getType() != StoragePolicyType.LOCAL
                && status != null
                && status.isReady();
    }

    private Mono<StoragePolicy> requireRecordable(StoragePolicy policy) {
        if (isRecordable(policy)) {
            return Mono.just(policy);
        }
        var name = policy.getMetadata() == null ? null : policy.getMetadata().getName();
        var type = policy.getSpec() == null ? null : policy.getSpec().getType();
        log.warn("录制的存储策略不可用 [{}]，类型 {}，是否就绪 {}", name, type, policy.getStatus() != null && policy.getStatus().isReady());
        return Mono.error(LIVE_STORAGE_POLICY_INVALID.exception());
    }

    private static boolean isNotFound(Throwable error) {
        return error instanceof ApiException apiException
                && apiException.getStatusCode().value() == HttpStatus.NOT_FOUND.value();
    }
}
