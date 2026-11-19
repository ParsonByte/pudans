package cn.tolim.pudans.core.storage.endpoint;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.OneDriveOAuthService;
import cn.tolim.pudans.core.storage.StoragePolicyService;
import cn.tolim.pudans.core.storage.StoragePolicyValidator;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactoryRegistry;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StoragePolicyEndpointTest {

    @Mock
    StoragePolicyValidator validator;

    @Mock
    StoragePolicyService policyService;

    @Mock
    StorageService storageService;

    @Mock
    StorageDriverFactoryRegistry registry;

    @Mock
    OneDriveOAuthService oneDriveOAuthService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new StoragePolicyEndpoint(
                validator, policyService, storageService, registry, oneDriveOAuthService);
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
        when(oneDriveOAuthService.authorizeUrl("od")).thenReturn(Mono.just(
                new OneDriveOAuthService.OAuthUrl(
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
