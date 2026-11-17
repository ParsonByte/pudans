package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.RequestBodyValidationException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.yaml.snakeyaml.Yaml;

@ExtendWith(MockitoExtension.class)
class ParameterSettingGroupServiceTest {

    @Mock
    ParameterSettingFetcher fetcher;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    ParameterSettingSecretCipher secretCipher;

    private ParameterSettingGroupService service;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        lenient()
                .when(secretCipher.isEncrypted(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).startsWith("enc:"));
        lenient()
                .when(secretCipher.encrypt(anyString()))
                .thenAnswer(invocation -> "enc:" + invocation.getArgument(0, String.class));
        lenient()
                .when(secretCipher.decrypt(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).replaceFirst("^enc:", ""));
        service = new ParameterSettingGroupService(fetcher, client, eventPublisher, validator, secretCipher);
    }

    @Test
    void siteUpdateComputesAnnouncementVersion() throws Exception {
        var configMap = configMap();
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("site", ObjectNode.class))
                .thenReturn(Mono.just(objectNode("{\"siteName\":\"pudans\",\"siteUrl\":\"\"}")));
        when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

        var body = objectNode("{\"siteName\":\"Pudans\",\"siteAnnouncement\":\"hello\"}");

        StepVerifier.create(service.updateGroup("site", body))
                .assertNext(result -> assertThat(result.restartRequired()).isFalse())
                .verifyComplete();

        var stored = read(configMap.getData().get("site"));
        assertThat(stored.get("siteName").asString()).isEqualTo("Pudans");
        assertThat(stored.get("announcementVersion").asString()).isEqualTo(sha256("hello"));
        assertThat(read(configMap.getData().get("basic")).get("title").asString()).isEqualTo("Pudans");
    }

    @Test
    void mailUpdateRestoresMaskedSecretsAndEncryptsNewOnes() {
        var configMap = configMap();
        configMap.getData().put("mail", "{\"smtpPassword\":\"enc:old\"}");
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("mail", ObjectNode.class)).thenReturn(Mono.just(objectNode(
                "{\"smtpHost\":\"\",\"smtpPort\":587,\"smtpPassword\":\"enc:old\"}")));
        when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

        var body = objectNode(
                "{\"smtpHost\":\"smtp.example.com\",\"smtpPort\":465,\"smtpUsername\":\"new-user\","
                        + "\"smtpPassword\":\"********\"}");

        StepVerifier.create(service.updateGroup("mail", body))
                .assertNext(result -> assertThat(result.restartRequired()).isFalse())
                .verifyComplete();

        var stored = read(configMap.getData().get("mail"));
        assertThat(stored.get("smtpUsername").asString()).isEqualTo("enc:new-user");
        assertThat(stored.get("smtpPassword").asString()).isEqualTo("enc:old");
        assertThat(stored.get("host").asString()).isEqualTo("smtp.example.com");
        assertThat(stored.get("port").asInt()).isEqualTo(465);
        assertThat(stored.get("password").asString()).isEqualTo("enc:old");
    }

    @Test
    void getGroupMasksSensitiveValues() {
        when(fetcher.fetch("mail", ObjectNode.class)).thenReturn(Mono.just(objectNode(
                "{\"smtpUsername\":\"enc:user\",\"smtpPassword\":\"enc:secret\",\"password\":\"enc:secret\"}")));

        StepVerifier.create(service.getGroup("mail"))
                .assertNext(node -> {
                    assertThat(node.get("smtpUsername").asString())
                            .isEqualTo(ParameterSettingSensitiveFields.MASK);
                    assertThat(node.get("smtpPassword").asString())
                            .isEqualTo(ParameterSettingSensitiveFields.MASK);
                    assertThat(node.get("password").asString())
                            .isEqualTo(ParameterSettingSensitiveFields.MASK);
                })
                .verifyComplete();
    }

    @Test
    void legacyServerUpdateUsesModernValidation() {
        var configMap = configMap();
        configMap.getData().put("server", "{\"siteId\":\"site-1\"}");
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("server", ObjectNode.class))
                .thenReturn(Mono.just(objectNode("{\"siteId\":\"site-1\"}")));

        StepVerifier.create(service.updateLegacyGroup("server", objectNode("{\"siteId\":\"site-2\"}")))
                .expectError(RequestBodyValidationException.class)
                .verify();
    }

    @Test
    void siteIdIsReadOnly() {
        var configMap = configMap();
        configMap.getData().put("server", "{\"siteId\":\"site-1\"}");
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("server", ObjectNode.class))
                .thenReturn(Mono.just(objectNode("{\"siteId\":\"site-1\"}")));

        StepVerifier.create(service.updateGroup("server", objectNode("{\"siteId\":\"site-2\"}")))
                .expectError(RequestBodyValidationException.class)
                .verify();
    }

    @Test
    void masterKeyChangeRequiresRestart() {
        var configMap = configMap();
        configMap.getData().put("server", "{\"siteId\":\"site-1\",\"masterKey\":\"enc:old\"}");
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("server", ObjectNode.class))
                .thenReturn(Mono.just(objectNode("{\"siteId\":\"site-1\",\"masterKey\":\"enc:old\"}")));
        when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

        StepVerifier.create(service.updateGroup("server", objectNode("{\"masterKey\":\"rotated\"}")))
                .assertNext(result -> assertThat(result.restartRequired()).isTrue())
                .verifyComplete();

        var stored = read(configMap.getData().get("server"));
        assertThat(stored.get("masterKey").asString()).isEqualTo("enc:rotated");
    }

    @Test
    void invalidSiteUrlIsRejected() {
        var configMap = configMap();
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("site", ObjectNode.class))
                .thenReturn(Mono.just(objectNode("{\"siteName\":\"pudans\",\"siteUrl\":\"\"}")));

        StepVerifier.create(service.updateGroup("site", objectNode("{\"siteUrl\":\"ftp://example.com\"}")))
                .expectError(RequestBodyValidationException.class)
                .verify();
    }

    @Test
    void invalidCronIsRejected() {
        var configMap = configMap();
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("server", ObjectNode.class))
                .thenReturn(Mono.just(objectNode("{\"siteId\":\"site-1\",\"gcCron\":\"0 0 3 * * *\"}")));

        StepVerifier.create(service.updateGroup("server", objectNode("{\"gcCron\":\"not-a-cron\"}")))
                .expectError(RequestBodyValidationException.class)
                .verify();
    }

    @Test
    void legacyCaptchaWriteMirrorsToTheNewFields() {
        var configMap = configMap();
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

        StepVerifier.create(service.updateLegacyGroup(
                        "captcha", objectNode("{\"enabled\":true,\"provider\":\"image\"}")))
                .assertNext(result -> assertThat(result.restartRequired()).isFalse())
                .verifyComplete();

        var stored = read(configMap.getData().get("captcha"));
        assertThat(stored.get("loginCaptchaEnabled").asBoolean()).isTrue();
        assertThat(stored.get("captchaProvider").asString()).isEqualTo("image");
    }

    @Test
    void legacyBasicWriteMirrorsToTheSiteGroup() {
        var configMap = configMap();
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

        StepVerifier.create(service.updateLegacyGroup("basic", objectNode("{\"title\":\"Legacy\"}")))
                .assertNext(result -> assertThat(result.restartRequired()).isFalse())
                .verifyComplete();

        var site = read(configMap.getData().get("site"));
        assertThat(site.get("siteName").asString()).isEqualTo("Legacy");
    }

    @Test
    void legacyReadRejectsUnknownGroup() {
        StepVerifier.create(service.getLegacyGroup("unknown"))
                .expectError(ServerWebInputException.class)
                .verify();
    }

    @Test
    void legacyUpdateRejectsUnknownGroup() {
        StepVerifier.create(service.updateLegacyGroup("unknown", objectNode("{\"key\":\"value\"}")))
                .expectError(ServerWebInputException.class)
                .verify();
    }

    @Test
    void everyNewGroupAcceptsAllDefaultFieldsAndReturnsThem() throws Exception {
        var defaults = defaultGroups();
        for (var group : ParameterSettingGroupService.NEW_GROUPS) {
            var configMap = configMap();
            when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
            when(fetcher.fetch(group, ObjectNode.class)).thenReturn(Mono.just(defaults.get(group)));
            when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

            StepVerifier.create(service.updateGroup(group, defaults.get(group).deepCopy()))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            var stored = read(configMap.getData().get(group));
            assertThat(stored.properties().stream().map(java.util.Map.Entry::getKey))
                    .as("stored fields of " + group)
                    .containsAll(defaults.get(group).properties().stream().map(java.util.Map.Entry::getKey).toList());

            StepVerifier.create(service.getGroup(group))
                    .assertNext(node -> assertThat(node.properties().stream().map(java.util.Map.Entry::getKey))
                            .as("read fields of " + group)
                            .containsAll(defaults.get(group).properties().stream()
                                    .map(java.util.Map.Entry::getKey)
                                    .toList()))
                    .verifyComplete();
        }
    }

    @Test
    void authenticationUpdateRestoresMaskedNestedSecretsAndEncryptsNewOnes() {
        var configMap = configMap();
        configMap.getData()
                .put(
                        "authentication",
                        "{\"feishu\":{\"appSecret\":\"enc:old\"},"
                                + "\"oauth2\":{\"providers\":[{\"registrationId\":\"github\","
                                + "\"clientSecret\":\"enc:old-client\"}]}}");
        when(fetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(fetcher.fetch("authentication", ObjectNode.class))
                .thenReturn(Mono.just(objectNode(
                        "{\"totp\":{\"enabled\":true},\"oauth2\":{\"providers\":[]},"
                                + "\"oidc\":{\"providers\":[]},\"feishu\":{},\"pat\":{\"enabled\":true}}")));
        when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

        var body = objectNode(
                "{\"feishu\":{\"appSecret\":\"********\"},"
                        + "\"oauth2\":{\"providers\":[{\"registrationId\":\"github\","
                        + "\"clientSecret\":\"********\"},{\"registrationId\":\"gitlab\","
                        + "\"clientSecret\":\"new-secret\"}]}}");

        StepVerifier.create(service.updateGroup("authentication", body))
                .assertNext(result -> assertThat(result.restartRequired()).isFalse())
                .verifyComplete();

        var stored = read(configMap.getData().get("authentication"));
        assertThat(stored.get("feishu").get("appSecret").asString()).isEqualTo("enc:old");
        var providers = stored.get("oauth2").get("providers");
        assertThat(providers.get(0).get("clientSecret").asString()).isEqualTo("enc:old-client");
        assertThat(providers.get(1).get("clientSecret").asString()).isEqualTo("enc:new-secret");
    }

    @Test
    void getAuthenticationGroupMasksNestedSecrets() {
        when(fetcher.fetch("authentication", ObjectNode.class))
                .thenReturn(Mono.just(objectNode(
                        "{\"feishu\":{\"appSecret\":\"enc:secret\"},"
                                + "\"oauth2\":{\"providers\":[{\"registrationId\":\"github\","
                                + "\"clientSecret\":\"enc:client\"}]},"
                                + "\"oidc\":{\"providers\":[{\"registrationId\":\"example\","
                                + "\"clientSecret\":\"enc:oidc\"}]}}")));

        StepVerifier.create(service.getGroup("authentication"))
                .assertNext(node -> {
                    assertThat(node.get("feishu").get("appSecret").asString())
                            .isEqualTo(ParameterSettingSensitiveFields.MASK);
                    assertThat(node.get("oauth2")
                                    .get("providers")
                                    .get(0)
                                    .get("clientSecret")
                                    .asString())
                            .isEqualTo(ParameterSettingSensitiveFields.MASK);
                    assertThat(node.get("oidc")
                                    .get("providers")
                                    .get(0)
                                    .get("clientSecret")
                                    .asString())
                            .isEqualTo(ParameterSettingSensitiveFields.MASK);
                })
                .verifyComplete();
    }

    private static Map<String, ObjectNode> defaultGroups() throws Exception {
        try (InputStream input = new ClassPathResource("extensions/config-template-configmap.yaml").getInputStream()) {
            var document = (Map<String, Object>) new Yaml().load(input);
            var data = (Map<String, String>) document.get("data");
            var result = new HashMap<String, ObjectNode>();
            for (var group : ParameterSettingGroupService.NEW_GROUPS) {
                result.put(group, objectNode(data.get(group)));
            }
            return result;
        }
    }

    private static ConfigMap configMap() {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName("parameter-settings");
        configMap.setMetadata(metadata);
        configMap.setData(new HashMap<>());
        return configMap;
    }

    private static ObjectNode objectNode(String json) {
        JsonNode node = JsonMapper.shared().readTree(json);
        return (ObjectNode) node;
    }

    private static ObjectNode read(String json) {
        return objectNode(json);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
