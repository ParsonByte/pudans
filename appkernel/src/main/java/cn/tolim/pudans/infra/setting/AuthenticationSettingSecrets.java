package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.utils.SettingUtils;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class AuthenticationSettingSecrets {

    private static final String MASK = ParameterSettingSensitiveFields.MASK;

    private static final String CLIENT_SECRET = "clientSecret";

    private AuthenticationSettingSecrets() {}

    // 对飞书与各认证提供者的密钥统一脱敏。
    static void mask(ObjectNode root) {
        if (root == null) {
            return;
        }
        maskSecret(object(root, "feishu"), "appSecret");
        maskProviders(root, "oauth2");
        maskProviders(root, "oidc");
    }

    // 掩码值沿用已保存的密钥，避免更新时被覆盖。
    static void restoreMasked(ObjectNode incoming, ObjectNode existing) {
        if (incoming == null) {
            return;
        }
        restoreSecret(object(incoming, "feishu"), object(existing, "feishu"), "appSecret");
        restoreProviders(incoming, existing, "oauth2");
        restoreProviders(incoming, existing, "oidc");
    }

    // 加密所有非空的认证密钥后再持久化。
    static void encrypt(ObjectNode root, ParameterSettingSecretCipher cipher) {
        if (root == null) {
            return;
        }
        encryptSecret(object(root, "feishu"), "appSecret", cipher);
        encryptProviders(root, "oauth2", cipher);
        encryptProviders(root, "oidc", cipher);
    }

    // 仅解密副本用于校验，不回写持久化数据。
    static void decryptForValidation(ObjectNode root, ParameterSettingSecretCipher cipher) {
        if (root == null) {
            return;
        }
        decryptSecret(object(root, "feishu"), "appSecret", cipher);
        decryptProviders(root, "oauth2", cipher);
        decryptProviders(root, "oidc", cipher);
    }

    // 解密认证设置中的密钥字段，供运行时使用。
    static void decrypt(ParameterSetting.Authentication authentication, ParameterSettingSecretCipher cipher) {
        if (authentication == null) {
            return;
        }
        var feishu = authentication.getFeishu();
        if (feishu != null) {
            feishu.setAppSecret(decrypt(feishu.getAppSecret(), cipher));
        }
        var oauth2 = authentication.getOauth2();
        if (oauth2 != null && oauth2.getProviders() != null) {
            oauth2.getProviders().forEach(provider -> provider.setClientSecret(decrypt(provider.getClientSecret(), cipher)));
        }
        var oidc = authentication.getOidc();
        if (oidc != null && oidc.getProviders() != null) {
            oidc.getProviders().forEach(provider -> provider.setClientSecret(decrypt(provider.getClientSecret(), cipher)));
        }
    }

    // 逐项脱敏提供者列表中的客户端密钥。
    private static void maskProviders(ObjectNode root, String group) {
        var providers = providers(root, group);
        if (providers == null) {
            return;
        }
        for (int index = 0; index < providers.size(); index++) {
            maskSecret(object(providers.get(index)), CLIENT_SECRET);
        }
    }

    // 按注册标识匹配旧配置并恢复被掩码的密钥。
    private static void restoreProviders(ObjectNode incoming, ObjectNode existing, String group) {
        var incomingProviders = providers(incoming, group);
        if (incomingProviders == null) {
            return;
        }
        var existingProviders = providers(existing, group);
        for (int index = 0; index < incomingProviders.size(); index++) {
            var incomingProvider = object(incomingProviders.get(index));
            if (incomingProvider == null) {
                continue;
            }
            var registrationId = SettingUtils.text(incomingProvider, "registrationId");
            restoreSecret(
                    incomingProvider,
                    findProvider(existingProviders, registrationId),
                    CLIENT_SECRET);
        }
    }

    // 加密提供者列表中的客户端密钥。
    private static void encryptProviders(ObjectNode root, String group, ParameterSettingSecretCipher cipher) {
        var providers = providers(root, group);
        if (providers == null) {
            return;
        }
        for (int index = 0; index < providers.size(); index++) {
            encryptSecret(object(providers.get(index)), CLIENT_SECRET, cipher);
        }
    }

    // 解密提供者列表中的客户端密钥副本。
    private static void decryptProviders(ObjectNode root, String group, ParameterSettingSecretCipher cipher) {
        var providers = providers(root, group);
        if (providers == null) {
            return;
        }
        for (int index = 0; index < providers.size(); index++) {
            decryptSecret(object(providers.get(index)), CLIENT_SECRET, cipher);
        }
    }

    // 在旧配置中按注册标识查找对应提供者。
    private static ObjectNode findProvider(ArrayNode providers, String registrationId) {
        if (providers == null || registrationId == null) {
            return null;
        }
        for (int index = 0; index < providers.size(); index++) {
            var provider = object(providers.get(index));
            if (provider != null && registrationId.equals(SettingUtils.text(provider, "registrationId"))) {
                return provider;
            }
        }
        return null;
    }

    // 安全读取提供者数组，结构不符时返回空。
    private static ArrayNode providers(ObjectNode root, String group) {
        var groupNode = object(root, group);
        if (groupNode == null) {
            return null;
        }
        var node = groupNode.get("providers");
        return node instanceof ArrayNode array ? array : null;
    }

    // 仅当节点为对象时返回，否则返回空。
    private static ObjectNode object(JsonNode node) {
        return node instanceof ObjectNode objectNode ? objectNode : null;
    }

    // 读取子对象字段，父节点或字段缺失时返回空。
    private static ObjectNode object(ObjectNode root, String field) {
        return root == null ? null : object(root.get(field));
    }

    // 仅对非空字符串密钥写入掩码。
    private static void maskSecret(ObjectNode node, String field) {
        if (node == null) {
            return;
        }
        var value = node.get(field);
        if (value != null && value.isString() && !value.asString().isEmpty()) {
            node.put(field, MASK);
        }
    }

    // 恢复掩码字段；无历史值时移除该字段。
    private static void restoreSecret(ObjectNode incoming, ObjectNode existing, String field) {
        if (incoming == null) {
            return;
        }
        var value = incoming.get(field);
        if (value == null || !value.isString() || !MASK.equals(value.asString())) {
            return;
        }
        var previous = existing == null ? null : existing.get(field);
        if (previous != null && previous.isString() && !previous.asString().isEmpty()) {
            incoming.put(field, previous.asString());
        } else {
            incoming.remove(field);
        }
    }

    // 对未加密的非空密钥执行加密。
    private static void encryptSecret(ObjectNode node, String field, ParameterSettingSecretCipher cipher) {
        if (node == null) {
            return;
        }
        var value = node.get(field);
        if (value != null
                && value.isString()
                && !value.asString().isEmpty()
                && !cipher.isEncrypted(value.asString())) {
            node.put(field, cipher.encrypt(value.asString()));
        }
    }

    // 对已加密的密钥执行解密。
    private static void decryptSecret(ObjectNode node, String field, ParameterSettingSecretCipher cipher) {
        if (node == null) {
            return;
        }
        var value = node.get(field);
        if (value != null && value.isString() && cipher.isEncrypted(value.asString())) {
            node.put(field, cipher.decrypt(value.asString()));
        }
    }

    // 仅当值已加密时才解密，否则原样返回。
    private static String decrypt(String value, ParameterSettingSecretCipher cipher) {
        return value == null || value.isEmpty() || !cipher.isEncrypted(value) ? value : cipher.decrypt(value);
    }
}
