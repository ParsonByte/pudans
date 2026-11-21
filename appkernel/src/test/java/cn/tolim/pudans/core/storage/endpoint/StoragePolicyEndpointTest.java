package cn.tolim.pudans.core.storage.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.OneDriveOAuth;
import cn.tolim.pudans.core.storage.Policies;
import cn.tolim.pudans.core.storage.StoragePolicyValidator;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactoryRegistry;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StoragePolicyEndpointTest {

    @Mock
    StoragePolicyValidator validator;

    @Mock
    Policies policies;

    @Mock
    StorageTransfer storageTransfer;

    @Mock
    StorageDriverFactoryRegistry registry;

    @Mock
    OneDriveOAuth oneDriveOAuth;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new StoragePolicyEndpoint(
                validator, policies, storageTransfer, registry, oneDriveOAuth);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsStoragePolicyTypes() {
        when(registry.descriptors()).thenReturn(List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.LOCAL, "本地存储", "", false, Set.of())));

        client.get()
                .uri("/storagepolicies/-/types")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].type")
                .isEqualTo("local");
    }

    @Test
    void returnsTheOneDriveAuthorizeUrl() {
        when(oneDriveOAuth.authorizeUrl(eq("od"), any())).thenReturn(Mono.just(
                new OneDriveOAuth.OAuthUrl(
                        "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                        "state-token",
                        "https://pudans.example.com/apis/api.pudans.tolim.cn/v1alpha1/storagepolicies/-/oauth/callback")));

        client.get()
                .uri("/storagepolicies/od/oauth/authorize")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.state")
                .isEqualTo("state-token");
    }
}
