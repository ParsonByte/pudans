package cn.tolim.pudans.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.security.contract.RememberMeToken;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SensitiveExtensionMixinsTest {

    private final JsonMapper mapper = mapper();

    private static JsonMapper mapper() {
        var builder = JsonMapper.builder();
        SensitiveExtensionMixins.apply(builder);
        return builder.build();
    }

    @Test
    void userSecretsAreHiddenFromResponsesButAcceptedOnInput() {
        var user = new User();
        var spec = new User.UserSpec();
        spec.setPassword("hash");
        spec.setTotpEncryptedSecret("encrypted");
        user.setSpec(spec);

        var json = mapper.writeValueAsString(user);

        assertThat(json).doesNotContain("password").doesNotContain("totpEncryptedSecret");
        var written = mapper.readValue("{\"spec\":{\"password\":\"hash\"}}", User.class);
        assertThat(written.getSpec().getPassword()).isEqualTo("hash");
    }

    @Test
    void rememberMeTokenMaterialIsHiddenFromResponses() {
        var token = new RememberMeToken();
        var spec = new RememberMeToken.Spec();
        spec.setSeries("series");
        spec.setTokenValue("token");
        spec.setPreviousTokenValue("previous");
        token.setSpec(spec);

        var json = mapper.writeValueAsString(token);

        assertThat(json).doesNotContain("series").doesNotContain("tokenValue").doesNotContain("previousTokenValue");
    }

    @Test
    void webhookSecretIsHiddenFromResponsesButAcceptedOnInput() {
        var webhook = new Webhook();
        var spec = new Webhook.Spec();
        spec.setSecret("secret");
        webhook.setSpec(spec);

        var json = mapper.writeValueAsString(webhook);

        assertThat(json).doesNotContain("secret");
        var written = mapper.readValue("{\"spec\":{\"secret\":\"secret\"}}", Webhook.class);
        assertThat(written.getSpec().getSecret()).isEqualTo("secret");
    }

    @Test
    void storagePolicyCredentialsAreHiddenFromResponsesButAcceptedOnInput() {
        var policy = new StoragePolicy();
        var credentials = new StoragePolicy.Credentials();
        credentials.setAccessKey("access");
        credentials.setSecretKey("secret");
        credentials.setToken("token");
        credentials.setClientSecret("client-secret");
        policy.getSpec().setCredentials(credentials);

        var json = mapper.writeValueAsString(policy);

        assertThat(json)
                .doesNotContain("accessKey")
                .doesNotContain("secretKey")
                .doesNotContain("clientSecret");
        var written = mapper.readValue(
                "{\"spec\":{\"credentials\":{\"accessKey\":\"access\"}}}", StoragePolicy.class);
        assertThat(written.getSpec().getCredentials().getAccessKey()).isEqualTo("access");
    }

    @Test
    void secretValuesAreHiddenFromResponsesButAcceptedOnInput() throws Exception {
        var secret = new Secret();
        secret.setType("Opaque");
        secret.setData(Map.of("certificate", "hidden-data".getBytes(StandardCharsets.UTF_8)));
        secret.setStringData(Map.of("password", "hidden-string"));

        var json = mapper.writeValueAsString(secret);

        assertThat(json)
                .doesNotContain("\"data\"")
                .doesNotContain("\"stringData\"")
                .doesNotContain(Base64.getEncoder().encodeToString("hidden-data".getBytes(StandardCharsets.UTF_8)))
                .doesNotContain("hidden-string");
        var written = mapper.readValue(
                """
                {
                  "data": {"certificate": "aGlkZGVuLWRhdGE="},
                  "stringData": {"password": "hidden-string"}
                }
                """,
                Secret.class);
        assertThat(new String(written.getData().get("certificate"), StandardCharsets.UTF_8))
                .isEqualTo("hidden-data");
        assertThat(written.getStringData()).containsEntry("password", "hidden-string");
    }

    @Test
    void secretValuesRemainVisibleToTheStorageMapper() throws Exception {
        var secret = new Secret();
        secret.setType("Opaque");
        secret.setData(Map.of("certificate", "hidden-data".getBytes(StandardCharsets.UTF_8)));
        secret.setStringData(Map.of("password", "hidden-string"));
        var storageMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        var json = storageMapper.writeValueAsString(secret);

        assertThat(json)
                .contains("\"data\"")
                .contains("\"stringData\"")
                .contains(Base64.getEncoder().encodeToString("hidden-data".getBytes(StandardCharsets.UTF_8)))
                .contains("hidden-string");
    }
}
