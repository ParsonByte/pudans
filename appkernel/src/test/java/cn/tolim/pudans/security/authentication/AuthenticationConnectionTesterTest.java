package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.setting.ParameterSettingSensitiveFields;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.AuthenticationConnectionTester.ConnectionTestRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthenticationConnectionTesterTest {

    @Mock
    SecurityParameters securitySettings;

    @Mock
    FeishuClient feishuClient;

    private AuthenticationConnectionTester tester;

    @BeforeEach
    void setUp() {
        tester = new AuthenticationConnectionTester(
                securitySettings, feishuClient, WebClient.builder().build());
    }

    @Test
    void testsFeishuWithSuppliedCredentials() {
        when(securitySettings.feishu())
                .thenReturn(new SecurityParameters.FeishuSettings(
                        false, null, null, null, null, null, 0, null));
        when(feishuClient.testConnection("app", "secret")).thenReturn(Mono.just("token"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "feishu", null, null, null, null, null, null, null, "app", "secret", null)))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.message()).contains("飞书连接成功");
                })
                .verifyComplete();
    }

    @Test
    void testsFeishuWithTheStoredSecretWhenItIsMasked() {
        when(securitySettings.feishu())
                .thenReturn(new SecurityParameters.FeishuSettings(
                        true, "stored-app", "stored-secret", null, null, null, 0, null));
        when(feishuClient.testConnection("stored-app", "stored-secret"))
                .thenReturn(Mono.just("token"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "feishu",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "stored-app",
                        ParameterSettingSensitiveFields.MASK,
                        null)))
                .assertNext(result -> assertThat(result.success()).isTrue())
                .verifyComplete();
    }

    @Test
    void testsOauth2WithTheStoredSecretWhenItIsMasked() {
        var provider = new ParameterSetting.OAuth2Provider();
        provider.setRegistrationId("github");
        provider.setTokenUri("https://idp.example.com/token");
        provider.setClientId("client");
        provider.setClientSecret("stored");
        when(securitySettings.oauth2Providers()).thenReturn(List.of(provider));
        tester = new AuthenticationConnectionTester(
                securitySettings, feishuClient, webClient(HttpStatus.OK, "{\"access_token\":\"x\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2",
                        "github",
                        null,
                        null,
                        null,
                        null,
                        null,
                        ParameterSettingSensitiveFields.MASK,
                        null,
                        null,
                        null)))
                .assertNext(result -> assertThat(result.success()).isTrue())
                .verifyComplete();
    }

    @Test
    void testsTheOauth2TokenEndpoint() {
        tester = new AuthenticationConnectionTester(
                securitySettings, feishuClient, webClient(HttpStatus.OK, "{\"access_token\":\"x\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2",
                        null,
                        null,
                        null,
                        "https://idp.example.com/token",
                        null,
                        "client",
                        "secret",
                        null,
                        null,
                        null)))
                .assertNext(result -> assertThat(result.success()).isTrue())
                .verifyComplete();
    }

    @Test
    void reportsInvalidClientCredentials() {
        tester = new AuthenticationConnectionTester(
                securitySettings,
                feishuClient,
                webClient(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2",
                        null,
                        null,
                        null,
                        "https://idp.example.com/token",
                        null,
                        "client",
                        "wrong",
                        null,
                        null,
                        null)))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.message()).contains("无效");
                })
                .verifyComplete();
    }

    @Test
    void acceptsProvidersThatDoNotSupportClientCredentials() {
        tester = new AuthenticationConnectionTester(
                securitySettings,
                feishuClient,
                webClient(HttpStatus.BAD_REQUEST, "{\"error\":\"unsupported_grant_type\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2",
                        null,
                        null,
                        null,
                        "https://idp.example.com/token",
                        null,
                        "client",
                        "secret",
                        null,
                        null,
                        null)))
                .assertNext(result -> assertThat(result.success()).isTrue())
                .verifyComplete();
    }

    @Test
    void testsOidcDiscovery() {
        tester = new AuthenticationConnectionTester(
                securitySettings,
                feishuClient,
                webClient(
                        HttpStatus.OK,
                        "{\"authorization_endpoint\":\"https://idp.example.com/authorize\","
                                + "\"token_endpoint\":\"https://idp.example.com/token\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oidc", null, "https://idp.example.com", null, null, null, null, null, null, null, null)))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.message()).contains("发现文档");
                })
                .verifyComplete();
    }

    @Test
    void rejectsIncompleteOauth2Configuration() {

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2", null, null, null, null, null, "client", "secret", null, null, null)))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.message()).contains("令牌地址");
                })
                .verifyComplete();
    }

    @Test
    void rejectsALoopbackTokenEndpoint() {
        tester = new AuthenticationConnectionTester(
                securitySettings, feishuClient, webClient(HttpStatus.OK, "{\"access_token\":\"x\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2", null, null, null, "http://127.0.0.1:8080/token", null,
                        "client", "secret", null, null, null)))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.message()).contains("内网");
                })
                .verifyComplete();
    }

    @Test
    void rejectsAMetadataIssuerUri() {
        tester = new AuthenticationConnectionTester(
                securitySettings, feishuClient, webClient(HttpStatus.OK, "{}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oidc", null, "http://169.254.169.254", null, null, null, null, null, null, null, null)))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.message()).contains("内网");
                })
                .verifyComplete();
    }

    @Test
    void rejectsTheStoredSecretWhenTheTokenUriChanged() {
        when(securitySettings.oauth2Providers()).thenReturn(List.of(oauth2Provider()));
        tester = new AuthenticationConnectionTester(
                securitySettings, feishuClient, webClient(HttpStatus.OK, "{\"access_token\":\"x\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2", "github", null, null, "https://attacker.example.com/token", null,
                        null, ParameterSettingSensitiveFields.MASK, null, null, null)))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.message()).contains("Client Secret");
                })
                .verifyComplete();
    }

    @Test
    void acceptsASuppliedSecretWithACustomTokenUri() {
        when(securitySettings.oauth2Providers()).thenReturn(List.of(oauth2Provider()));
        tester = new AuthenticationConnectionTester(
                securitySettings, feishuClient, webClient(HttpStatus.OK, "{\"access_token\":\"x\"}"));

        StepVerifier.create(tester.test(new ConnectionTestRequest(
                        "oauth2", "github", null, null, "https://attacker.example.com/token", null,
                        "client", "supplied", null, null, null)))
                .assertNext(result -> assertThat(result.success()).isTrue())
                .verifyComplete();
    }

    private static ParameterSetting.OAuth2Provider oauth2Provider() {
        var provider = new ParameterSetting.OAuth2Provider();
        provider.setRegistrationId("github");
        provider.setTokenUri("https://idp.example.com/token");
        provider.setClientId("client");
        provider.setClientSecret("stored");
        return provider;
    }

    private static WebClient webClient(HttpStatus status, String body) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();
    }
}
