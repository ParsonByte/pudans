package cn.tolim.pudans.core.storage.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.storage.Policies;
import cn.tolim.pudans.core.storage.StorageRoutes;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.extension.contract.Metadata;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageRouteEndpointTest {

    @Mock
    StorageRoutes storageRoutes;

    @Mock
    StorageTransfer storageTransfer;

    @Mock
    Policies policies;

    @Mock
    AuditLog auditLog;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new StorageRouteEndpoint(storageRoutes, storageTransfer, policies, auditLog);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsConfiguredRoutes() {
        when(storageRoutes.list()).thenReturn(Flux.just(route()));

        client.get()
                .uri("/storageroutes")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name")
                .isEqualTo("creation-result")
                .jsonPath("$[0].spec.purpose")
                .isEqualTo("creation_result");
    }

    @Test
    void listsSupportedPurposesAndFallbackRules() {
        client.get()
                .uri("/storageroutes/-/purposes")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].purpose")
                .isEqualTo("creation_result")
                .jsonPath("$[0].name")
                .isEqualTo("creation-result")
                .jsonPath("$[0].fallbackToDefaultPolicy")
                .isEqualTo(true)
                .jsonPath("$[1].purpose")
                .isEqualTo("sandbox_snapshot")
                .jsonPath("$[1].fallbackToDefaultPolicy")
                .isEqualTo(false);
    }

    @Test
    void getsARouteByPurpose() {
        when(storageRoutes.get(StorageRoute.Purpose.CREATION_RESULT)).thenReturn(Mono.just(route()));

        client.get()
                .uri("/storageroutes/creation_result")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.spec.policyName")
                .isEqualTo("p1");
    }

    @Test
    void putsARouteAndRecordsAnAudit() {
        var spec = new StorageRoute.Spec();
        spec.setPolicyName("p1");
        spec.setEnabled(true);
        spec.setDescription("生成结果");
        when(storageRoutes.put(eq(StorageRoute.Purpose.CREATION_RESULT), any(StorageRoute.Spec.class)))
                .thenReturn(Mono.just(route()));
        when(auditLog.recordChild(
                        any(),
                        isNull(),
                        eq("PUT_STORAGE_ROUTE"),
                        eq("StorageRoute"),
                        eq("creation-result"),
                        eq("SUCCESS"),
                        anyMap(),
                        isNull()))
                .thenReturn(Mono.empty());

        client.put()
                .uri("/storageroutes/creation_result")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(spec)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.spec.policyName")
                .isEqualTo("p1");

        var captured = ArgumentCaptor.forClass(StorageRoute.Spec.class);
        verify(storageRoutes).put(eq(StorageRoute.Purpose.CREATION_RESULT), captured.capture());
        assertThat(captured.getValue().getPurpose()).isNull();
        assertThat(captured.getValue().getPolicyName()).isEqualTo("p1");
    }

    @Test
    void deletesARouteAndRecordsAnAudit() {
        when(storageRoutes.delete(StorageRoute.Purpose.SANDBOX_SNAPSHOT)).thenReturn(Mono.just(sandboxRoute()));
        when(auditLog.recordChild(
                        any(),
                        isNull(),
                        eq("DELETE_STORAGE_ROUTE"),
                        eq("StorageRoute"),
                        eq("sandbox-snapshot"),
                        eq("SUCCESS"),
                        anyMap(),
                        isNull()))
                .thenReturn(Mono.empty());

        client.delete()
                .uri("/storageroutes/sandbox_snapshot")
                .exchange()
                .expectStatus()
                .isNoContent();

        verify(auditLog).recordChild(
                any(),
                isNull(),
                eq("DELETE_STORAGE_ROUTE"),
                eq("StorageRoute"),
                eq("sandbox-snapshot"),
                eq("SUCCESS"),
                anyMap(),
                isNull());
    }

    @Test
    void testsTheResolvedReadyPolicyWithoutLeakingCredentials() {
        var policy = policy("p1");
        policy.getSpec().getCredentials().setAccessKey("super-secret");
        var result = new StorageTestResult(true, "ok", Instant.now());
        when(storageRoutes.resolve(StorageRoute.Purpose.CREATION_RESULT)).thenReturn(Mono.just(policy));
        when(storageTransfer.test("p1")).thenReturn(Mono.just(result));
        when(policies.recordTestResult("p1", result)).thenReturn(Mono.just(policy));
        when(auditLog.recordChild(
                        any(),
                        isNull(),
                        eq("TEST_STORAGE_ROUTE"),
                        eq("StorageRoute"),
                        eq("creation-result"),
                        eq("SUCCESS"),
                        anyMap(),
                        isNull()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/storageroutes/creation_result/-/test")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.message")
                .isEqualTo("ok");

        verify(auditLog).recordChild(
                any(),
                isNull(),
                eq("TEST_STORAGE_ROUTE"),
                eq("StorageRoute"),
                eq("creation-result"),
                eq("SUCCESS"),
                argThat(snapshot -> "p1".equals(snapshot.get("policyName"))
                        && !snapshot.toString().contains("super-secret")),
                isNull());
    }

    private static StorageRoute route() {
        var route = new StorageRoute();
        var metadata = new Metadata();
        metadata.setName("creation-result");
        route.setMetadata(metadata);
        route.getSpec().setPurpose(StorageRoute.Purpose.CREATION_RESULT);
        route.getSpec().setPolicyName("p1");
        route.getSpec().setEnabled(true);
        return route;
    }

    private static StorageRoute sandboxRoute() {
        var route = new StorageRoute();
        var metadata = new Metadata();
        metadata.setName("sandbox-snapshot");
        route.setMetadata(metadata);
        route.getSpec().setPurpose(StorageRoute.Purpose.SANDBOX_SNAPSHOT);
        route.getSpec().setPolicyName("p1");
        route.getSpec().setEnabled(true);
        return route;
    }

    private static StoragePolicy policy(String name) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName(name);
        policy.setMetadata(metadata);
        policy.getSpec().setDisplayName(name);
        policy.getSpec().setType(StoragePolicyType.LOCAL);
        var status = new StoragePolicy.Status();
        status.setReady(true);
        policy.setStatus(status);
        return policy;
    }
}
