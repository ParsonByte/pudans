package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.web.server.MockWebSession;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.contract.ExternalUrlSupplier;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OneDriveOAuthTest {

    @Mock
    Policies policies;

    @Mock
    TransferTokens transferTokens;

    @Mock
    ExternalUrlSupplier externalUrl;

    @Mock
    StorageDriverResolver driverResolver;

    @Mock
    StoragePolicySecretCipher secretCipher;

    @Mock
    ExchangeFunction exchangeFunction;

    private OneDriveOAuth service;

    @BeforeEach
    void setUp() {
        var webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        service = new OneDriveOAuth(
                policies, transferTokens, externalUrl, driverResolver, secretCipher, webClient);
    }

    @Test
    void buildsTheAuthorizeUrl() {
        var policy = policy(true);
        when(policies.get("od")).thenReturn(Mono.just(policy));
        when(transferTokens.signOAuthState(eq("od"), any(Duration.class))).thenReturn("state-token");
        when(externalUrl.get()).thenReturn(URI.create("https://pudans.example.com"));

        var result = service.authorizeUrl("od", exchange()).block();

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
        when(policies.get("od")).thenReturn(Mono.just(policy(true)));

        var result = service.status("od").block();

        assertThat(result).isNotNull();
        assertThat(result.authorized()).isTrue();
    }

    @Test
    void reportsMissingAuthorization() {
        when(policies.get("od")).thenReturn(Mono.just(policy(false)));

        var result = service.status("od").block();

        assertThat(result).isNotNull();
        assertThat(result.authorized()).isFalse();
    }

    @Test
    void storesTheRefreshTokenEncrypted() {
        var policy = policy(true);
        when(transferTokens.signOAuthState(eq("od"), any(Duration.class))).thenReturn("state");
        when(transferTokens.verifyOAuthState("state")).thenReturn("od");
        when(policies.get("od")).thenReturn(Mono.just(policy));
        when(secretCipher.decrypt("client-secret")).thenReturn("client-secret");
        when(secretCipher.encrypt("new-refresh")).thenReturn("enc:new-refresh");
        when(policies.update(policy)).thenReturn(Mono.just(policy));
        when(externalUrl.get()).thenReturn(URI.create("https://pudans.example.com"));
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(response("{\"refresh_token\":\"new-refresh\"}")));
        var exchange = exchange();
        service.authorizeUrl("od", exchange).block();

        var result = service.callback("code", "state", exchange).block();

        assertThat(result).isNotNull();
        assertThat(result.authorized()).isTrue();
        assertThat(policy.getSpec().getCredentials().getToken()).isEqualTo("enc:new-refresh");
        verify(driverResolver).evict("od");
    }

    // 回调状态必须来自发起授权的同一个会话。
    @Test
    void rejectsCallbackFromAnotherSession() {
        var policy = policy(true);
        when(transferTokens.signOAuthState(eq("od"), any(Duration.class))).thenReturn("state");
        when(transferTokens.verifyOAuthState("state")).thenReturn("od");
        when(policies.get("od")).thenReturn(Mono.just(policy));
        when(externalUrl.get()).thenReturn(URI.create("https://pudans.example.com"));
        service.authorizeUrl("od", exchange()).block();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.callback("code", "state", exchange()).block())
                .hasMessageContaining("授权状态无效");
    }

    @Test
    void resolvesTheDriveIdFromTheSite() {
        var policy = policy(true);
        when(policies.get("od")).thenReturn(Mono.just(policy));
        when(secretCipher.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(exchangeFunction.exchange(any()))
                .thenReturn(
                        Mono.just(response("{\"access_token\":\"at\",\"expires_in\":3600}")),
                        Mono.just(response("{\"id\":\"site-1\"}")),
                        Mono.just(response("{\"id\":\"drive-1\"}")));

        var driveId = service.resolveRoot("od", "https://contoso.sharepoint.com/sites/team").block();

        assertThat(driveId).isEqualTo("drive-1");
    }

    private static ClientResponse response(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.builder(MockServerHttpRequest.get("/").build())
                .session(new MockWebSession())
                .build();
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
