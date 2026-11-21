package cn.tolim.pudans.core.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.security.RememberMeToken;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.ai.AiEnvironmentVariable;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.ai.AiSandboxProvider;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.user.User;
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
    void aiEnvironmentVariableValueIsHiddenFromResponsesButAcceptedOnInput() {
        var variable = new AiEnvironmentVariable();
        var spec = new AiEnvironmentVariable.Spec();
        spec.setName("API_TOKEN");
        spec.setValue("env-secret");
        variable.setSpec(spec);

        var json = mapper.writeValueAsString(variable);

        assertThat(json).doesNotContain("env-secret");
        var written = mapper.readValue(
                "{\"spec\":{\"name\":\"API_TOKEN\",\"value\":\"env-secret\"}}", AiEnvironmentVariable.class);
        assertThat(written.getSpec().getValue()).isEqualTo("env-secret");
    }

    @Test
    void aiSandboxProviderSecretIsHiddenFromResponsesButAcceptedOnInput() {
        var provider = new AiSandboxProvider();
        var spec = new AiSandboxProvider.Spec();
        spec.setProvider(AiSandboxProvider.Provider.ALIYUN);
        spec.setAccessKeySecret("sandbox-secret");
        provider.setSpec(spec);

        var json = mapper.writeValueAsString(provider);

        assertThat(json).doesNotContain("sandbox-secret");
        var written = mapper.readValue(
                "{\"spec\":{\"provider\":\"ALIYUN\",\"accessKeySecret\":\"sandbox-secret\"}}",
                AiSandboxProvider.class);
        assertThat(written.getSpec().getAccessKeySecret()).isEqualTo("sandbox-secret");
    }

    @Test
    void aiProviderAccountKeyIsHiddenFromResponsesButAcceptedOnInput() {
        var account = new AiProviderAccount();
        var spec = new AiProviderAccount.Spec();
        spec.setApiKey("sk-secret");
        account.setSpec(spec);

        var json = mapper.writeValueAsString(account);

        assertThat(json).doesNotContain("sk-secret");
        var written = mapper.readValue("{\"spec\":{\"apiKey\":\"sk-secret\"}}", AiProviderAccount.class);
        assertThat(written.getSpec().getApiKey()).isEqualTo("sk-secret");
    }
}
