package cn.tolim.pudans.infra.setting;

import java.util.Map;
import java.util.Set;
import tools.jackson.databind.node.ObjectNode;

public enum ParameterSettingSensitiveFields {
    ;

    public static final String MASK = "********";

    private static final Map<String, Set<String>> SENSITIVE_FIELDS = Map.of(
            "mail", Set.of("smtpUsername", "smtpPassword", "username", "password"),
            "server", Set.of("masterKey", "hashIdSalt"));

    // 判断字段是否属于需要脱敏的敏感项。
    public static boolean isSensitive(String group, String field) {
        return SENSITIVE_FIELDS.getOrDefault(group, Set.of()).contains(field);
    }

    // 对已配置的敏感字段写入掩码。
    public static void mask(ObjectNode node, String group) {
        if (node == null) {
            return;
        }
        for (var field : SENSITIVE_FIELDS.getOrDefault(group, Set.of())) {
            var value = node.get(field);
            if (value != null && value.isString() && !value.asString().isEmpty()) {
                node.put(field, MASK);
            }
        }
    }

    // 掩码值沿用已保存内容，避免误清空密钥。
    public static void restoreMasked(ObjectNode incoming, ObjectNode existing, String group) {
        for (var field : SENSITIVE_FIELDS.getOrDefault(group, Set.of())) {
            var value = incoming.get(field);
            if (value != null && value.isString() && MASK.equals(value.asString())) {
                var previous = existing == null ? null : existing.get(field);
                if (previous != null && previous.isString()) {
                    incoming.put(field, previous.asString());
                } else {
                    incoming.remove(field);
                }
            }
        }
    }

    // 仅解密副本用于校验，不修改持久化数据。
    public static void decryptForValidation(ObjectNode node, String group, ParameterSettingSecretCipher cipher) {
        for (var field : SENSITIVE_FIELDS.getOrDefault(group, Set.of())) {
            var value = node.get(field);
            if (value != null && value.isString() && cipher.isEncrypted(value.asString())) {
                node.put(field, cipher.decrypt(value.asString()));
            }
        }
    }

    // 加密未加密的非空敏感字段。
    public static void encrypt(ObjectNode node, String group, ParameterSettingSecretCipher cipher) {
        for (var field : SENSITIVE_FIELDS.getOrDefault(group, Set.of())) {
            var value = node.get(field);
            if (value != null
                    && value.isString()
                    && !value.asString().isEmpty()
                    && !cipher.isEncrypted(value.asString())) {
                node.put(field, cipher.encrypt(value.asString()));
            }
        }
    }

    // 解密单个敏感值，空值原样返回。
    public static String decrypt(String storedValue, ParameterSettingSecretCipher cipher) {
        return storedValue == null ? null : cipher.decrypt(storedValue);
    }
}
