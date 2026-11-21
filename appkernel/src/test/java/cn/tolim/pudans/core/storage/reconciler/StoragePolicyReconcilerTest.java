package cn.tolim.pudans.core.storage.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.storage.resource.StoragePolicy;
import cn.tolim.pudans.contract.storage.resource.StoragePolicyType;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.storage.StoragePolicyValidator;
import cn.tolim.pudans.core.storage.StoragePolicySecretCipher;
import cn.tolim.pudans.core.storage.FileRecords;

@ExtendWith(MockitoExtension.class)
class StoragePolicyReconcilerTest {

    @Mock
    ExtensionClient client;

    @Mock
    StoragePolicyValidator validator;

    @Mock
    StoragePolicySecretCipher secretCipher;

    @Mock
    FileRecords fileService;

    @Mock
    StorageDriverResolver driverResolver;

    private StoragePolicyReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new StoragePolicyReconciler(client, validator, secretCipher, fileService, driverResolver);
    }

    @Test
    void encryptsCredentialsAndAddsTheFinalizer() {
        var policy = policy("p1");
        policy.getSpec().getCredentials().setAccessKey("ak");
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Optional.of(policy));
        when(secretCipher.isEncrypted("ak")).thenReturn(false);
        when(secretCipher.encrypt("ak")).thenReturn("enc:ak");
        when(validator.validatePolicy(policy))
                .thenReturn(new StoragePolicyValidator.ValidationResult(true, List.of()));
        doNothing().when(client).update(any(StoragePolicy.class));

        reconciler.reconcile(new Reconciler.Request("p1"));

        assertThat(policy.getMetadata().getFinalizers()).contains("storage-policy-protection");
        assertThat(policy.getSpec().getCredentials().getAccessKey()).isEqualTo("enc:ak");
        assertThat(policy.getStatus().isReady()).isTrue();
        verify(client).update(policy);
    }

    @Test
    void refusesToDeleteADefaultPolicy() {
        var policy = policy("p1");
        policy.getMetadata().setDeletionTimestamp(Instant.now());
        policy.getSpec().setDefaultPolicy(true);
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Optional.of(policy));
        when(fileService.existsByPolicy("p1")).thenReturn(Mono.just(false));
        doNothing().when(client).update(any(StoragePolicy.class));

        reconciler.reconcile(new Reconciler.Request("p1"));

        assertThat(policy.getMetadata().getDeletionTimestamp()).isNull();
        assertThat(policy.getStatus().getMessage()).contains("默认");
        verify(client).update(policy);
    }

    @Test
    void deletesAnUnusedPolicyAndEvictsTheDriver() {
        var policy = policy("p1");
        policy.getMetadata().setDeletionTimestamp(Instant.now());
        policy.getMetadata().setFinalizers(Set.of("storage-policy-protection"));
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Optional.of(policy));
        when(fileService.existsByPolicy("p1")).thenReturn(Mono.just(false));
        doNothing().when(client).update(any(StoragePolicy.class));

        reconciler.reconcile(new Reconciler.Request("p1"));

        assertThat(policy.getMetadata().getFinalizers()).isEmpty();
        verify(driverResolver).evict("p1");
        verify(client).update(policy);
    }

    @Test
    void ignoresMissingPolicies() {
        when(client.fetch(StoragePolicy.class, "p1")).thenReturn(Optional.empty());

        reconciler.reconcile(new Reconciler.Request("p1"));

        verify(client, org.mockito.Mockito.never()).update(any());
    }

    private static StoragePolicy policy(String name) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName(name);
        policy.setMetadata(metadata);
        policy.getSpec().setDisplayName(name);
        policy.getSpec().setType(StoragePolicyType.LOCAL);
        policy.getSpec().setDirNameRule("uploads/{path}");
        policy.getSpec().setFileNameRule("{randomkey8}_{originname}");
        return policy;
    }
}
