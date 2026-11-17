package cn.tolim.pudans.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.core.extension.RememberMeToken;
import cn.tolim.pudans.contract.core.extension.Webhook;
import cn.tolim.pudans.contract.core.extension.user.User;
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
}
