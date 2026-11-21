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

    // 为敏感字段挂载混入，阻止序列化泄露。
    static void apply(JsonMapper.Builder builder) {
        builder.addMixIn(User.UserSpec.class, UserSpecMixin.class);
        builder.addMixIn(RememberMeToken.Spec.class, RememberMeTokenSpecMixin.class);
        builder.addMixIn(Webhook.Spec.class, WebhookSpecMixin.class);
        builder.addMixIn(StoragePolicy.Credentials.class, StoragePolicyCredentialsMixin.class);
    }

    abstract static class UserSpecMixin {

        // 密码只允许写入，序列化时不输出。
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getPassword();

        // TOTP 密钥只允许写入，避免泄露。
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getTotpEncryptedSecret();
    }

    abstract static class RememberMeTokenSpecMixin {

        // 序列化时忽略令牌系列标识。
        @JsonIgnore
        abstract String getSeries();

        // 序列化时忽略当前令牌值。
        @JsonIgnore
        abstract String getTokenValue();

        // 序列化时忽略上一轮令牌值。
        @JsonIgnore
        abstract String getPreviousTokenValue();
    }

    abstract static class WebhookSpecMixin {

        // Webhook 密钥只允许写入。
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getSecret();
    }

    abstract static class StoragePolicyCredentialsMixin {

        // 访问密钥只允许写入。
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getAccessKey();

        // 私有密钥只允许写入。
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getSecretKey();

        // 临时令牌只允许写入。
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getToken();

        // 客户端密钥只允许写入。
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        abstract String getClientSecret();
    }
}
