package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.api.ParameterSetting;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class AuthenticationSettingSecrets {

    private static final String MASK = ParameterSettingSensitiveFields.MASK;

    private static final String CLIENT_SECRET = "clientSecret";

    private AuthenticationSettingSecrets() {}

    static void mask(ObjectNode root) {
        if (root == null) {
            return;
        }
        maskSecret(object(root, "feishu"), "appSecret");
        maskProviders(root, "oauth2");
        maskProviders(root, "oidc");
    }

    static void restoreMasked(ObjectNode incoming, ObjectNode existing) {
        if (incoming == null) {
            return;
        }
        restoreSecret(object(incoming, "feishu"), object(existing, "feishu"), "appSecret");
        restoreProviders(incoming, existing, "oauth2");
        restoreProviders(incoming, existing, "oidc");
    }

    static void encrypt(ObjectNode root, ParameterSettingSecretCipher cipher) {
        if (root == null) {
            return;
        }
        encryptSecret(object(root, "feishu"), "appSecret", cipher);
        encryptProviders(root, "oauth2", cipher);
        encryptProviders(root, "oidc", cipher);
    }

    static void decryptForValidation(ObjectNode root, ParameterSettingSecretCipher cipher) {
        if (root == null) {
            return;
        }
        decryptSecret(object(root, "feishu"), "appSecret", cipher);
        decryptProviders(root, "oauth2", cipher);
        decryptProviders(root, "oidc", cipher);
    }

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

    private static void maskProviders(ObjectNode root, String group) {
        var providers = providers(root, group);
        if (providers == null) {
            return;
        }
        for (int index = 0; index < providers.size(); index++) {
            maskSecret(object(providers.get(index)), CLIENT_SECRET);
        }
    }

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
            var registrationId = text(incomingProvider, "registrationId");
            restoreSecret(
                    incomingProvider,
                    findProvider(existingProviders, registrationId),
                    CLIENT_SECRET);
        }
    }

    private static void encryptProviders(ObjectNode root, String group, ParameterSettingSecretCipher cipher) {
        var providers = providers(root, group);
        if (providers == null) {
            return;
        }
        for (int index = 0; index < providers.size(); index++) {
            encryptSecret(object(providers.get(index)), CLIENT_SECRET, cipher);
        }
    }

    private static void decryptProviders(ObjectNode root, String group, ParameterSettingSecretCipher cipher) {
        var providers = providers(root, group);
        if (providers == null) {
            return;
        }
        for (int index = 0; index < providers.size(); index++) {
            decryptSecret(object(providers.get(index)), CLIENT_SECRET, cipher);
        }
    }

    private static ObjectNode findProvider(ArrayNode providers, String registrationId) {
        if (providers == null || registrationId == null) {
            return null;
        }
        for (int index = 0; index < providers.size(); index++) {
            var provider = object(providers.get(index));
            if (provider != null && registrationId.equals(text(provider, "registrationId"))) {
                return provider;
            }
        }
        return null;
    }

    private static ArrayNode providers(ObjectNode root, String group) {
        var groupNode = object(root, group);
        if (groupNode == null) {
            return null;
        }
        var node = groupNode.get("providers");
        return node instanceof ArrayNode array ? array : null;
    }

    private static ObjectNode object(JsonNode node) {
        return node instanceof ObjectNode objectNode ? objectNode : null;
    }

    private static ObjectNode object(ObjectNode root, String field) {
        return root == null ? null : object(root.get(field));
    }

    private static String text(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        var value = node.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static void maskSecret(ObjectNode node, String field) {
        if (node == null) {
            return;
        }
        var value = node.get(field);
        if (value != null && value.isString() && !value.asString().isEmpty()) {
            node.put(field, MASK);
        }
    }

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

    private static void decryptSecret(ObjectNode node, String field, ParameterSettingSecretCipher cipher) {
        if (node == null) {
            return;
        }
        var value = node.get(field);
        if (value != null && value.isString() && cipher.isEncrypted(value.asString())) {
            node.put(field, cipher.decrypt(value.asString()));
        }
    }

    private static String decrypt(String value, ParameterSettingSecretCipher cipher) {
        return value == null || value.isEmpty() || !cipher.isEncrypted(value) ? value : cipher.decrypt(value);
    }
}
