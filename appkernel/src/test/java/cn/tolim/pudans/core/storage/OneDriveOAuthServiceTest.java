package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.ExternalUrlSupplier;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OneDriveOAuthServiceTest {

    @Mock
    StoragePolicyService policyService;

    @Mock
    StorageTokenService tokenService;

    @Mock
    ExternalUrlSupplier externalUrl;

    @Mock
    StorageDriverResolver driverResolver;

    @Mock
    StoragePolicySecretCipher secretCipher;

    private OneDriveOAuthService service;

    @BeforeEach
    void setUp() {
        service = new OneDriveOAuthService(
                policyService, tokenService, externalUrl, driverResolver, secretCipher);
    }

    @Test
    void buildsTheAuthorizeUrl() {
        var policy = policy(true);
        when(policyService.get("od")).thenReturn(Mono.just(policy));
        when(tokenService.signOAuthState(eq("od"), any(Duration.class))).thenReturn("state-token");
        when(externalUrl.get()).thenReturn(URI.create("https://pudans.example.com"));

        var result = service.authorizeUrl("od").block();

        assertThat(result).isNotNull();
        assertThat(result.url())
                .startsWith("https://login.microsoftonline.com/contoso/oauth2/v2.0/authorize?")
                .contains("client_id=client-id")
                .contains("state=state-token")
                .contains("redirect_uri=https%3A%2F%2Fpudans.example.com%2Fapis%2Fapi.pudans.tolim.cn%2Fv1alpha1%2Fstoragepolicies%2F-%2Foauth%2Fcallback");
        assertThat(result.redirectUri())
                .isEqualTo("https://pudans.example.com/apis/api.pudans.tolim.cn/v1alpha1/storagepolicies/-/oauth/callback");
    }

    @Test
    void reportsConfiguredAuthorization() {
        when(policyService.get("od")).thenReturn(Mono.just(policy(true)));

        var result = service.status("od").block();

        assertThat(result).isNotNull();
        assertThat(result.authorized()).isTrue();
    }

    @Test
    void reportsMissingAuthorization() {
        when(policyService.get("od")).thenReturn(Mono.just(policy(false)));

        var result = service.status("od").block();

        assertThat(result).isNotNull();
        assertThat(result.authorized()).isFalse();
    }

    private static StoragePolicy policy(boolean authorized) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName("od");
        policy.setMetadata(metadata);
        var spec = policy.getSpec();
        spec.setDisplayName("onedrive");
        spec.setType(StoragePolicyType.ONEDRIVE);
        spec.setBucketName("client-id");
        spec.getSettings().setTenantId("contoso");
        spec.getCredentials().setClientSecret("client-secret");
        if (authorized) {
            spec.getCredentials().setToken("refresh-token");
        }
        return policy;
    }
}
