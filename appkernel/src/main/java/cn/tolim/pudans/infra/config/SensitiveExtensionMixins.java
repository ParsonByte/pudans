package cn.tolim.pudans.infra.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.tolim.pudans.contract.security.resource.RememberMeToken;
import cn.tolim.pudans.contract.webhook.resource.Webhook;
import cn.tolim.pudans.contract.ai.resource.AiEnvironmentVariable;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.ai.resource.AiSandboxProvider;
import cn.tolim.pudans.contract.storage.resource.StoragePolicy;
import cn.tolim.pudans.contract.user.resource.User;
import tools.jackson.databind.json.JsonMapper;

final class SensitiveExtensionMixins {

    private SensitiveExtensionMixins() {}

    static void apply(JsonMapper.Builder builder) {
        builder.addMixIn(User.UserSpec.class, UserSpecMixin.class);
        builder.addMixIn(RememberMeToken.Spec.class, RememberMeTokenSpecMixin.class);
        builder.addMixIn(Webhook.Spec.class, WebhookSpecMixin.class);
        builder.addMixIn(StoragePolicy.Credentials.class, StoragePolicyCredentialsMixin.class);
        builder.addMixIn(AiProviderAccount.Spec.class, AiProviderAccountSpecMixin.class);
        builder.addMixIn(AiProviderAccount.BalanceProbe.class, AiProviderBalanceProbeMixin.class);
        builder.addMixIn(AiEnvironmentVariable.Spec.class, AiEnvironmentVariableSpecMixin.class);
        builder.addMixIn(AiSandboxProvider.Spec.class, AiSandboxProviderSpecMixin.class);
    }

    abstract static class UserSpecMixin {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getPassword();

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getTotpEncryptedSecret();
    }

    abstract static class RememberMeTokenSpecMixin {

        @JsonIgnore
        abstract String getSeries();

        @JsonIgnore
        abstract String getTokenValue();

        @JsonIgnore
        abstract String getPreviousTokenValue();
    }

    abstract static class WebhookSpecMixin {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getSecret();
    }

    abstract static class StoragePolicyCredentialsMixin {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getAccessKey();

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getSecretKey();

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getToken();

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getClientSecret();
    }

    abstract static class AiProviderAccountSpecMixin {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getApiKey();
    }

    abstract static class AiProviderBalanceProbeMixin {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getToken();
    }

    abstract static class AiEnvironmentVariableSpecMixin {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getValue();
    }

    abstract static class AiSandboxProviderSpecMixin {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getAccessKeySecret();
    }
}
