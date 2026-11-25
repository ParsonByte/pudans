package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageRoutesTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    Policies policies;

    private StorageRoutes routes;

    @BeforeEach
    void setUp() {
        routes = new StorageRoutes(client, policies);
    }

    @Test
    void usesTheReadyPolicyConfiguredForCreationResults() {
        when(client.fetch(StorageRoute.class, "creation-result"))
                .thenReturn(Mono.just(route(StorageRoute.Purpose.CREATION_RESULT, "p1", true)));
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Mono.just(policy("p1", true)));

        var result = routes.resolve(StorageRoute.Purpose.CREATION_RESULT).block();

        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getName()).isEqualTo("p1");
        verify(policies, never()).defaultPolicy();
    }

    @Test
    void fallsBackToTheDefaultPolicyWhenCreationResultHasNoRoute() {
        when(client.fetch(StorageRoute.class, "creation-result")).thenReturn(Mono.empty());
        when(policies.defaultPolicy()).thenReturn(Mono.just(policy("default", true)));

        var result = routes.resolve(StorageRoute.Purpose.CREATION_RESULT).block();

        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getName()).isEqualTo("default");
    }

    @Test
    void fallsBackWhenTheCreationResultRouteIsDisabled() {
        when(client.fetch(StorageRoute.class, "creation-result"))
                .thenReturn(Mono.just(route(StorageRoute.Purpose.CREATION_RESULT, "p1", false)));
        when(policies.defaultPolicy()).thenReturn(Mono.just(policy("default", true)));

        var result = routes.resolve(StorageRoute.Purpose.CREATION_RESULT).block();

        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getName()).isEqualTo("default");
        verify(client, never()).fetch(StoragePolicy.class, "p1");
    }

    @Test
    void failsWhenTheConfiguredRoutePolicyIsMissing() {
        when(client.fetch(StorageRoute.class, "creation-result"))
                .thenReturn(Mono.just(route(StorageRoute.Purpose.CREATION_RESULT, "missing", true)));
        when(client.fetch(StoragePolicy.class, "missing")).thenReturn(Mono.empty());

        assertApiError(
                StorageRoute.Purpose.CREATION_RESULT,
                HttpStatus.SERVICE_UNAVAILABLE,
                "storage-route-policy-missing");
        verify(policies, never()).defaultPolicy();
    }

    @Test
    void failsWhenTheConfiguredRoutePolicyIsNotReady() {
        when(client.fetch(StorageRoute.class, "creation-result"))
                .thenReturn(Mono.just(route(StorageRoute.Purpose.CREATION_RESULT, "p1", true)));
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Mono.just(policy("p1", false)));

        assertApiError(
                StorageRoute.Purpose.CREATION_RESULT,
                HttpStatus.SERVICE_UNAVAILABLE,
                "storage-route-policy-not-ready");
        verify(policies, never()).defaultPolicy();
    }

    @Test
    void failsWhenSandboxSnapshotHasNoRoute() {
        when(client.fetch(StorageRoute.class, "sandbox-snapshot")).thenReturn(Mono.empty());

        assertApiError(
                StorageRoute.Purpose.SANDBOX_SNAPSHOT,
                HttpStatus.SERVICE_UNAVAILABLE,
                "storage-route-not-configured");
        verify(policies, never()).defaultPolicy();
    }

    @Test
    void failsWhenTheSandboxSnapshotRouteIsDisabled() {
        when(client.fetch(StorageRoute.class, "sandbox-snapshot"))
                .thenReturn(Mono.just(route(StorageRoute.Purpose.SANDBOX_SNAPSHOT, "p1", false)));

        assertApiError(
                StorageRoute.Purpose.SANDBOX_SNAPSHOT,
                HttpStatus.SERVICE_UNAVAILABLE,
                "storage-route-not-configured");
        verify(policies, never()).defaultPolicy();
    }

    @Test
    void usesTheConfiguredSandboxSnapshotPolicy() {
        when(client.fetch(StorageRoute.class, "sandbox-snapshot"))
                .thenReturn(Mono.just(route(StorageRoute.Purpose.SANDBOX_SNAPSHOT, "p1", true)));
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Mono.just(policy("p1", true)));

        var result = routes.resolve(StorageRoute.Purpose.SANDBOX_SNAPSHOT).block();

        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getName()).isEqualTo("p1");
        verify(policies, never()).defaultPolicy();
    }

    @Test
    void createsANewRouteFromThePurposePath() {
        var requested = new StorageRoute.Spec();
        requested.setPolicyName("p1");
        requested.setDescription("生成结果");
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Mono.just(policy("p1", true)));
        when(client.fetch(StorageRoute.class, "creation-result")).thenReturn(Mono.empty());
        when(client.create(any(StorageRoute.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = routes.put(StorageRoute.Purpose.CREATION_RESULT, requested).block();

        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getName()).isEqualTo("creation-result");
        assertThat(result.getSpec().getPurpose()).isEqualTo(StorageRoute.Purpose.CREATION_RESULT);
        assertThat(result.getSpec().getPolicyName()).isEqualTo("p1");
        assertThat(result.getSpec().isEnabled()).isTrue();
    }

    @Test
    void rejectsAPutWhosePurposeDoesNotMatchThePath() {
        var requested = new StorageRoute.Spec();
        requested.setPurpose(StorageRoute.Purpose.SANDBOX_SNAPSHOT);
        requested.setPolicyName("p1");

        assertThatThrownBy(() -> routes.put(StorageRoute.Purpose.CREATION_RESULT, requested).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var apiError = (ApiException) error;
                    assertThat(apiError.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiError.getCode()).isEqualTo("storage-route-purpose-mismatch");
                });
        verify(client, never()).create(any(StorageRoute.class));
    }

    @Test
    void rejectsPutWhenTheTargetPolicyDoesNotExist() {
        var requested = new StorageRoute.Spec();
        requested.setPolicyName("missing");
        when(client.fetch(StoragePolicy.class, "missing")).thenReturn(Mono.empty());

        assertThatThrownBy(() -> routes.put(StorageRoute.Purpose.CREATION_RESULT, requested).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var apiError = (ApiException) error;
                    assertThat(apiError.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiError.getCode()).isEqualTo("storage-route-policy-missing");
                });
        verify(client, never()).create(any(StorageRoute.class));
    }

    private void assertApiError(
            StorageRoute.Purpose purpose, HttpStatus status, String code) {
        assertThatThrownBy(() -> routes.resolve(purpose).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var apiError = (ApiException) error;
                    assertThat(apiError.getStatusCode()).isEqualTo(status);
                    assertThat(apiError.getCode()).isEqualTo(code);
                });
    }

    private static StorageRoute route(StorageRoute.Purpose purpose, String policyName, boolean enabled) {
        var route = new StorageRoute();
        var metadata = new Metadata();
        metadata.setName(purpose.routeName());
        route.setMetadata(metadata);
        route.getSpec().setPurpose(purpose);
        route.getSpec().setPolicyName(policyName);
        route.getSpec().setEnabled(enabled);
        return route;
    }

    private static StoragePolicy policy(String name, boolean ready) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName(name);
        policy.setMetadata(metadata);
        policy.getSpec().setDisplayName(name);
        policy.getSpec().setType(StoragePolicyType.LOCAL);
        var status = new StoragePolicy.Status();
        status.setReady(ready);
        if (!ready) {
            status.setMessage("尚未完成测试");
        }
        policy.setStatus(status);
        return policy;
    }
}
