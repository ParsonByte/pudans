package cn.tolim.pudans.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.contract.Secret;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class SecretCreateResponseTest {

    private static final String NAME = "created-secret";

    private static final String DATA_VALUE = "hidden-data-value";

    private static final String STRING_DATA_VALUE = "hidden-string-value";

    private ReactiveExtensionClient extensionClient;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        extensionClient = mock(ReactiveExtensionClient.class);
        var policy = mock(BuiltinEntityPolicy.class);
        when(policy.checkCreate(any(GroupVersionKind.class), any(Unstructured.class)))
                .thenReturn(Mono.empty());
        when(extensionClient.create(any(Unstructured.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(extensionClient.get(eq(Secret.class), eq(NAME))).thenReturn(Mono.just(secret()));
        var mapper = httpMapper();
        var strategies = HandlerStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
                })
                .build();
        var router = new ExtensionRouterFunctionFactory(
                        Scheme.buildFromType(Secret.class), extensionClient, policy)
                .create();
        webTestClient = WebTestClient.bindToRouterFunction(router)
                .handlerStrategies(strategies)
                .build();
    }

    private static JsonMapper httpMapper() {
        var builder = JsonMapper.builder();
        SensitiveExtensionMixins.apply(builder);
        return builder.build();
    }

    private static Secret secret() {
        var metadata = new Metadata();
        metadata.setName(NAME);
        var secret = new Secret();
        secret.setMetadata(metadata);
        secret.setType("Opaque");
        secret.setData(Map.of("certificate", DATA_VALUE.getBytes(StandardCharsets.UTF_8)));
        secret.setStringData(Map.of("password", STRING_DATA_VALUE));
        return secret;
    }

    @Test
    void createResponseUsesTheTypedSecretAndMasksSecretValues() {
        var data = Base64.getEncoder().encodeToString(DATA_VALUE.getBytes(StandardCharsets.UTF_8));
        var response = webTestClient
                .post()
                .uri("/apis/api.pudans.tolim.cn/v1alpha1/secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "apiVersion": "v1alpha1",
                          "kind": "Secret",
                          "metadata": {"name": "%s"},
                          "type": "Opaque",
                          "data": {"certificate": "%s"},
                          "stringData": {"password": "%s"}
                        }
                        """
                        .formatted(NAME, data, STRING_DATA_VALUE))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches("Location", ".*/secrets/" + NAME)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(response)
                .contains("\"name\":\"" + NAME + "\"")
                .doesNotContain("\"data\"")
                .doesNotContain("\"stringData\"")
                .doesNotContain(data)
                .doesNotContain(STRING_DATA_VALUE);
        verify(extensionClient).get(Secret.class, NAME);
    }
}
