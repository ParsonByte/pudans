package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.storage.Policies;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LiveStoragePolicyResolverTest {

    @Mock
    private Policies policies;

    @InjectMocks
    private LiveStoragePolicyResolver resolver;

    @Test
    void rejectsLocalPolicy() {
        when(policies.get("local")).thenReturn(Mono.just(policy("local", StoragePolicyType.LOCAL, true)));

        assertThatThrownBy(() -> resolver.resolve("local").block())
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                        .isEqualTo(LiveErrorCode.LIVE_STORAGE_POLICY_INVALID.name()));
    }

    @Test
    void rejectsPolicyThatIsNotReady() {
        when(policies.get("s3")).thenReturn(Mono.just(policy("s3", StoragePolicyType.S3, false)));

        assertThatThrownBy(() -> resolver.resolve("s3").block())
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                        .isEqualTo(LiveErrorCode.LIVE_STORAGE_POLICY_INVALID.name()));
    }

    @Test
    void mapsMissingDefaultPolicyToMissingCode() {
        when(policies.defaultPolicy())
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE, "storage-policy-default-missing", "未配置默认策略")));

        assertThatThrownBy(() -> resolver.resolve(" ").block())
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                        .isEqualTo(LiveErrorCode.LIVE_STORAGE_POLICY_MISSING.name()));
    }

    @Test
    void mapsUnknownPolicyToInvalidCode() {
        when(policies.get("missing"))
                .thenReturn(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "storage-policy-not-found", "不存在")));

        assertThatThrownBy(() -> resolver.resolve("missing").block())
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                        .isEqualTo(LiveErrorCode.LIVE_STORAGE_POLICY_INVALID.name()));
    }

    @Test
    void acceptsRemoteReadyPolicy() {
        var policy = policy("s3", StoragePolicyType.S3, true);
        when(policies.get("s3")).thenReturn(Mono.just(policy));

        var resolved = resolver.resolve("s3").block();

        assertThat(resolved).isSameAs(policy);
        assertThat(LiveStoragePolicyResolver.isRecordable(policy)).isTrue();
    }

    private static StoragePolicy policy(String name, StoragePolicyType type, boolean ready) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName(name);
        policy.setMetadata(metadata);
        policy.getSpec().setType(type);
        policy.getStatus().setReady(ready);
        return policy;
    }
}
