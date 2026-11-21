package cn.tolim.pudans.core.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ParameterSettingDefaultsTest {

    private static final Map<String, Set<String>> EXPECTED_FIELDS = Map.of(
            "site",
            Set.of(
                    "siteName",
                    "siteDescription",
                    "siteUrl",
                    "footerCode",
                    "siteAnnouncement",
                    "logoDark",
                    "logoLight",
                    "favicon",
                    "iconMedium",
                    "iconLarge"),
            "session",
            Set.of(
                    "passkeyLoginEnabled",
                    "exposeEmailToSignedInUsers",
                    "defaultUserGroup",
                    "avatarStoragePath",
                    "avatarMaxSize",
                    "avatarDimension",
                    "rsaLoginEncryptionEnabled",
                    "argon2PasswordStorageEnabled"),
            "captcha",
            Set.of(
                    "loginCaptchaEnabled",
                    "recoveryCaptchaEnabled",
                    "captchaProvider",
                    "captchaMode",
                    "captchaLength",
                    "captchaInterferenceText",
                    "captchaInterferenceDots",
                    "captchaUseHollowLine",
                    "captchaUseNoise",
                    "captchaUseInterferenceText",
                    "captchaUseWaveLine",
                    "captchaUseSineLine"),
            "mail",
            Set.of(
                    "mailFromName",
                    "mailFromAddress",
                    "smtpHost",
                    "smtpPort",
                    "mailReplyTo",
                    "smtpForceSsl",
                    "smtpConnectionTtlSeconds",
                    "mailTemplates"),
            "audit", Set.of("auditRecordRules"),
            "storage", Set.of("trashRetentionDays", "uploadSessionTtlHours", "downloadTokenTtlSeconds"),
            "authentication", Set.of("totp", "oauth2", "oidc", "feishu", "pat"),
            "server",
            Set.of("tempPath", "siteId", "accessTokenTtlSeconds", "refreshTokenTtlSeconds", "gcCron"));

    @Test
    void defaultConfigMapContainsEveryNonSensitiveField() throws Exception {
        try (InputStream input = new ClassPathResource("extensions/config/config-configmap-template-configmap.yaml").getInputStream()) {
            var document = (Map<String, Object>) new Yaml().load(input);
            var data = (Map<String, String>) document.get("data");

            EXPECTED_FIELDS.forEach((group, fields) -> {
                JsonNode node = JsonMapper.shared().readTree(data.get(group));
                assertThat(node.properties().stream().map(Map.Entry::getKey))
                        .as("fields of group " + group)
                        .containsAll(fields);
            });
        }
    }

    @Test
    void defaultConfigMapDoesNotStoreSensitiveValues() throws Exception {
        try (InputStream input = new ClassPathResource("extensions/config/config-configmap-template-configmap.yaml").getInputStream()) {
            var document = (Map<String, Object>) new Yaml().load(input);
            var data = (Map<String, String>) document.get("data");

            var mail = JsonMapper.shared().readTree(data.get("mail"));
            var server = JsonMapper.shared().readTree(data.get("server"));
            var authentication = JsonMapper.shared().readTree(data.get("authentication"));
            assertThat(mail.has("smtpUsername")).isFalse();
            assertThat(mail.has("smtpPassword")).isFalse();
            assertThat(server.has("masterKey")).isFalse();
            assertThat(server.has("hashIdSalt")).isFalse();
            assertThat(authentication.get("feishu").has("appSecret")).isFalse();
        }
    }

    @Test
    void defaultGroupsMatchTheDocumentedGroupNames() {
        assertThat(EXPECTED_FIELDS.keySet())
                .containsExactlyInAnyOrderElementsOf(
                        List.of("site", "session", "captcha", "mail", "audit", "storage", "authentication", "server"));
    }
}
