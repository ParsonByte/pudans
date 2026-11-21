package cn.tolim.pudans.infra.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.tolim.pudans.security.contract.RememberMeToken;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.user.User;
import tools.jackson.databind.json.JsonMapper;

final class SensitiveExtensionMixins {

    private SensitiveExtensionMixins() {}

    static void apply(JsonMapper.Builder builder) {
        builder.addMixIn(User.UserSpec.class, UserSpecMixin.class);
        builder.addMixIn(RememberMeToken.Spec.class, RememberMeTokenSpecMixin.class);
        builder.addMixIn(Webhook.Spec.class, WebhookSpecMixin.class);
        builder.addMixIn(StoragePolicy.Credentials.class, StoragePolicyCredentialsMixin.class);
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
}
