package cn.tolim.pudans.core.storage.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageRouteReconcilerTest {

    @Mock
    ExtensionClient client;

    private StorageRouteReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new StorageRouteReconciler(client);
    }

    @Test
    void addsTheProtectionFinalizerForAValidRoute() {
        var route = route(StorageRoute.Purpose.CREATION_RESULT, "p1", false);
        when(client.fetch(StorageRoute.class, "creation-result")).thenReturn(Optional.of(route));
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Optional.of(policy("p1")));

        reconciler.reconcile(new Reconciler.Request("creation-result"));

        assertThat(route.getMetadata().getFinalizers()).contains("storage-route-protection");
        verify(client).update(route);
    }

    @Test
    void doesNotProtectARouteWhoseNameDoesNotMatchItsPurpose() {
        var route = route(StorageRoute.Purpose.CREATION_RESULT, "p1", false);
        route.getMetadata().setName("sandbox-snapshot");
        when(client.fetch(StorageRoute.class, "sandbox-snapshot")).thenReturn(Optional.of(route));

        reconciler.reconcile(new Reconciler.Request("sandbox-snapshot"));

        assertThat(route.getMetadata().getFinalizers()).isNullOrEmpty();
        verify(client, never()).update(any(StorageRoute.class));
    }

    @Test
    void doesNotProtectARouteWhosePolicyIsMissing() {
        var route = route(StorageRoute.Purpose.SANDBOX_SNAPSHOT, "missing", false);
        when(client.fetch(StorageRoute.class, "sandbox-snapshot")).thenReturn(Optional.of(route));
        when(client.fetch(StoragePolicy.class, "missing")).thenReturn(Optional.empty());

        reconciler.reconcile(new Reconciler.Request("sandbox-snapshot"));

        assertThat(route.getMetadata().getFinalizers()).isNullOrEmpty();
        verify(client, never()).update(any(StorageRoute.class));
    }

    @Test
    void removesTheFinalizerWithoutDeletingFilesOnRouteDeletion() {
        var route = route(StorageRoute.Purpose.CREATION_RESULT, "p1", false);
        route.getMetadata().setDeletionTimestamp(Instant.now());
        route.getMetadata().setFinalizers(Set.of("storage-route-protection"));
        when(client.fetch(StorageRoute.class, "creation-result")).thenReturn(Optional.of(route));

        reconciler.reconcile(new Reconciler.Request("creation-result"));

        assertThat(route.getMetadata().getFinalizers()).isEmpty();
        verify(client).update(route);
        verify(client, never()).delete(any(StorageRoute.class));
    }

    @Test
    void ignoresMissingRoutes() {
        when(client.fetch(StorageRoute.class, "creation-result")).thenReturn(Optional.empty());

        reconciler.reconcile(new Reconciler.Request("creation-result"));

        verify(client, never()).update(any(StorageRoute.class));
    }

    private static StorageRoute route(
            StorageRoute.Purpose purpose, String policyName, boolean enabled) {
        var route = new StorageRoute();
        var metadata = new Metadata();
        metadata.setName(purpose.routeName());
        route.setMetadata(metadata);
        route.getSpec().setPurpose(purpose);
        route.getSpec().setPolicyName(policyName);
        route.getSpec().setEnabled(enabled);
        return route;
    }

    private static StoragePolicy policy(String name) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName(name);
        policy.setMetadata(metadata);
        return policy;
    }
}
