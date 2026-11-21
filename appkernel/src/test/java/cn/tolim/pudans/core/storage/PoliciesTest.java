package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.core.infra.exception.ApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PoliciesTest {

    @Mock
    ReactiveExtensionClient client;

    private Policies service;

    @BeforeEach
    void setUp() {
        service = new Policies(client);
    }

    @Test
    void findsAReadyDefaultPolicy() {
        when(client.listAll(eq(StoragePolicy.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(policy("p1", true, true)));

        var result = service.defaultPolicy().block();

        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getName()).isEqualTo("p1");
    }

    @Test
    void rejectsANotReadyDefaultPolicy() {
        when(client.listAll(eq(StoragePolicy.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(policy("p1", false, true)));

        assertThatThrownBy(() -> service.defaultPolicy().block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-policy-default-missing"));
    }

    @Test
    void requiresAReadyPolicy() {
        when(client.get(StoragePolicy.class, "p1")).thenReturn(Mono.just(policy("p1", false, false)));

        assertThatThrownBy(() -> service.requireReady("p1").block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-policy-not-ready"));
    }

    @Test
    void recordsTheTestResult() {
        var policy = policy("p1", true, false);
        when(client.get(StoragePolicy.class, "p1")).thenReturn(Mono.just(policy));
        when(client.update(any(StoragePolicy.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var testedAt = Instant.now();
        var result = new StorageTestResult(true, "ok", testedAt);

        var updated = service.recordTestResult("p1", result).block();

        assertThat(updated).isNotNull();
        assertThat(updated.getStatus().getMessage()).isEqualTo("ok");
        assertThat(updated.getStatus().getLastTestedAt()).isEqualTo(testedAt);
    }

    private static StoragePolicy policy(String name, boolean ready, boolean defaultPolicy) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName(name);
        policy.setMetadata(metadata);
        policy.getSpec().setDisplayName(name);
        policy.getSpec().setType(StoragePolicyType.LOCAL);
        policy.getSpec().setDefaultPolicy(defaultPolicy);
        var status = new StoragePolicy.Status();
        status.setReady(ready);
        policy.setStatus(status);
        return policy;
    }
}
