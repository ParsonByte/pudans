package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Secret;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiChannelSecrets {

    private static final String OPAQUE_TYPE = "opaque";

    private static final String DEFAULT_API_KEY = "apiKey";

    private final ReactiveExtensionClient client;

    private final AiChannelSecretCipher cipher;

    public Mono<Map<String, String>> resolve(AiChannelEntity channel) {
        var secretRef = channel.getSecretRef();
        var encrypted = channel.getCredentialCiphertext();
        var hasSecretRef = StringUtils.hasText(secretRef);
        var hasEncrypted = encrypted != null && !encrypted.isEmpty();
        if (hasSecretRef && hasEncrypted) {
            return Mono.error(conflict());
        }
        if (hasSecretRef) {
            return resolveSecret(secretRef.trim(), channel.getSecretKeys());
        }
        if (hasEncrypted) {
            return decrypt(encrypted);
        }
        return Mono.error(new ApiException(
                HttpStatus.CONFLICT,
                "ai-channel-credentials-missing",
                "AI 渠道尚未配置凭据。"));
    }

    public Map<String, String> mergeEncrypted(
            Map<String, String> existing, Map<String, String> requested) {
        var merged = new LinkedHashMap<>(existing == null ? Map.of() : existing);
        if (requested == null) {
            return merged;
        }
        requested.forEach((key, value) -> {
            if (!StringUtils.hasText(key)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-channel-credential-key-invalid",
                        "凭据键名不能为空。");
            }
            if (StringUtils.hasText(value)) {
                merged.put(key.trim(), cipher.encrypt(value));
            }
        });
        return Map.copyOf(merged);
    }

    public void validateConflict(String secretRef, Map<String, String> ciphertext) {
        if (StringUtils.hasText(secretRef) && ciphertext != null && !ciphertext.isEmpty()) {
            throw conflict();
        }
    }

    private Mono<Map<String, String>> resolveSecret(
            String secretRef, Map<String, String> secretKeys) {
        return client
                .fetch(Secret.class, secretRef)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "ai-channel-secret-ref-not-found",
                        "AI 渠道引用的 Secret 不存在。")))
                .flatMap(secret -> {
                    var type = secret.getType();
                    if (StringUtils.hasText(type) && !OPAQUE_TYPE.equalsIgnoreCase(type)) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "ai-channel-secret-type-invalid",
                                "AI 渠道 Secret 类型必须为 Opaque。"));
                    }
                    var values = secretValues(secret);
                    var mapping = secretKeys == null || secretKeys.isEmpty()
                            ? defaultMapping(values)
                            : secretKeys;
                    var resolved = new LinkedHashMap<String, String>();
                    for (var entry : mapping.entrySet()) {
                        var logicalKey = entry.getKey();
                        var secretKey = entry.getValue();
                        if (!StringUtils.hasText(logicalKey) || !StringUtils.hasText(secretKey)) {
                            return Mono.error(new ApiException(
                                    HttpStatus.CONFLICT,
                                    "ai-channel-secret-key-missing",
                                    "AI 渠道 Secret 键映射无效。"));
                        }
                        var value = values.get(secretKey);
                        if (!StringUtils.hasText(value)) {
                            return Mono.error(new ApiException(
                                    HttpStatus.CONFLICT,
                                    "ai-channel-secret-key-missing",
                                    "AI 渠道 Secret 缺少必需键。"));
                        }
                        resolved.put(logicalKey, value);
                    }
                    return Mono.just(Map.copyOf(resolved));
                });
    }

    private Mono<Map<String, String>> decrypt(Map<String, String> encrypted) {
        return Mono.fromCallable(() -> {
            var resolved = new LinkedHashMap<String, String>();
            encrypted.forEach((key, value) -> {
                if (StringUtils.hasText(value)) {
                    try {
                        resolved.put(key, cipher.decrypt(value));
                    } catch (RuntimeException error) {
                        throw decryptFailed(error);
                    }
                }
            });
            if (resolved.isEmpty()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "ai-channel-credentials-missing",
                        "AI 渠道尚未配置凭据。");
            }
            return Map.copyOf(resolved);
        });
    }

    private static Map<String, String> secretValues(Secret secret) {
        var values = new LinkedHashMap<String, String>();
        if (secret.getData() != null) {
            secret.getData()
                    .forEach((key, value) -> values.put(
                            key, value == null ? null : new String(value, StandardCharsets.UTF_8)));
        }
        if (secret.getStringData() != null) {
            values.putAll(secret.getStringData());
        }
        return values;
    }

    private static Map<String, String> defaultMapping(Map<String, String> values) {
        if (values.containsKey(DEFAULT_API_KEY)) {
            return Map.of(DEFAULT_API_KEY, DEFAULT_API_KEY);
        }
        return values.keySet().stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(key -> key, key -> key));
    }

    private static ApiException conflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "ai-channel-credentials-conflict",
                "AI 渠道不能同时配置 Secret 引用和加密凭据。");
    }

    private static ApiException decryptFailed(RuntimeException cause) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "ai-channel-credential-decrypt-failed",
                "AI 渠道加密凭据无法解密。",
                cause);
    }

}
