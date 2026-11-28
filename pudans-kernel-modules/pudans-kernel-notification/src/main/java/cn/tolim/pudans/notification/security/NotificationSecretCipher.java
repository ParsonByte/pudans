package cn.tolim.pudans.notification.security;

import cn.tolim.pudans.notification.configuration.NotificationProperties;
import cn.tolim.spissatus4j.contract.algorithm.AlgorithmId;
import cn.tolim.spissatus4j.contract.algorithm.KeyUsage;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.contract.api.SpissatusBuilder;
import cn.tolim.spissatus4j.contract.crypto.AAD;
import cn.tolim.spissatus4j.contract.crypto.DecryptRequest;
import cn.tolim.spissatus4j.contract.crypto.EncryptRequest;
import cn.tolim.spissatus4j.contract.crypto.EncryptedData;
import cn.tolim.spissatus4j.contract.crypto.Nonce;
import cn.tolim.spissatus4j.contract.key.KeyDescriptor;
import cn.tolim.spissatus4j.contract.key.KeyGenerationRequest;
import cn.tolim.spissatus4j.contract.key.KeyId;
import cn.tolim.spissatus4j.contract.key.KeyType;
import cn.tolim.spissatus4j.contract.key.KeyVersion;
import cn.tolim.spissatus4j.contract.policy.CallerContext;
import cn.tolim.spissatus4j.core.builder.SpissatusBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

// Encrypts provider credentials at rest with spissatus4j AES-GCM.
public class NotificationSecretCipher implements AutoCloseable {

    private static final String PREFIX = "spissatus:v1";
    private static final String AAD_CONTEXT = "pudans-notification-provider-config";
    private static final String CALLER_ID = "pudans-notification";
    private static final String SEPARATOR = ":";

    private final NotificationProperties properties;
    private final Spissatus externalSpissatus;

    private volatile CipherContext context;

    public NotificationSecretCipher(NotificationProperties properties) {
        this(properties, null);
    }

    public NotificationSecretCipher(NotificationProperties properties, Spissatus externalSpissatus) {
        this.properties = properties == null ? new NotificationProperties() : properties;
        this.externalSpissatus = externalSpissatus;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        CipherContext current = context();
        EncryptedData data = current.spissatus().crypto().encrypt(
                EncryptRequest.builder(AlgorithmId.AES_GCM, current.keyId(),
                                plaintext.getBytes(StandardCharsets.UTF_8), caller())
                        .aad(AAD.ofUtf8(AAD_CONTEXT))
                        .build());
        return String.join(SEPARATOR, PREFIX, current.keyId().value(),
                String.valueOf(data.keyVersion().value()),
                encode(data.nonce().asBytes()), encode(data.ciphertext()), encode(data.tag()));
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return encoded;
        }
        if (!encoded.startsWith(PREFIX + SEPARATOR)) {
            throw new IllegalArgumentException("Unsupported notification secret format");
        }
        String[] parts = encoded.split(SEPARATOR, -1);
        if (parts.length != 7) {
            throw new IllegalArgumentException("Malformed notification secret payload");
        }
        CipherContext current = context();
        byte[] plaintext = current.spissatus().crypto().decrypt(
                DecryptRequest.builder(AlgorithmId.AES_GCM, KeyId.of(parts[2]), decode(parts[5]),
                                caller())
                        .keyVersion(KeyVersion.of(Long.parseLong(parts[3])))
                        .nonce(Nonce.of(decode(parts[4])))
                        .tag(decode(parts[6]))
                        .aad(AAD.ofUtf8(AAD_CONTEXT))
                        .build());
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX + SEPARATOR);
    }

    @Override
    public void close() {
        CipherContext current = context;
        if (current != null && current.owned()) {
            current.spissatus().close();
        }
    }

    private CipherContext context() {
        CipherContext current = context;
        if (current == null) {
            synchronized (this) {
                current = context;
                if (current == null) {
                    current = buildContext();
                    context = current;
                }
            }
        }
        return current;
    }

    private CipherContext buildContext() {
        NotificationProperties.Crypto crypto = properties.getCrypto();
        boolean owned = externalSpissatus == null;
        Spissatus spissatus = owned ? buildSpissatus(crypto) : externalSpissatus;
        KeyDescriptor descriptor = spissatus.keyManager().generate(
                KeyGenerationRequest.builder(KeyType.AES, AlgorithmId.AES_GCM,
                                Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT))
                        .tenantId(crypto.getTenantId())
                        .build(),
                caller());
        return new CipherContext(spissatus, descriptor.keyId(), owned);
    }

    private Spissatus buildSpissatus(NotificationProperties.Crypto crypto) {
        SpissatusBuilder builder = SpissatusBuilders.builder()
                .discoverModules(true)
                .failClosed(true)
                .selfCheckOnStartup(false)
                .selfCheckFailureFailsStartup(false)
                .setting("spissatus.key.provider", crypto.getKeyProvider());
        if (crypto.getKeyDirectory() != null && !crypto.getKeyDirectory().isBlank()) {
            builder.setting("spissatus.key.file.directory", crypto.getKeyDirectory());
        }
        if (crypto.getMasterPassword() != null && !crypto.getMasterPassword().isBlank()) {
            builder.setting("spissatus.key.master.password", crypto.getMasterPassword());
        }
        if (crypto.getEnvironmentPrefix() != null && !crypto.getEnvironmentPrefix().isBlank()) {
            builder.setting("spissatus.key.environment.prefix", crypto.getEnvironmentPrefix());
        }
        if (crypto.getKeystorePath() != null && !crypto.getKeystorePath().isBlank()) {
            builder.setting("spissatus.key.keystore.path", crypto.getKeystorePath());
        }
        if (crypto.getKeystorePassword() != null && !crypto.getKeystorePassword().isBlank()) {
            builder.setting("spissatus.key.keystore.password", crypto.getKeystorePassword());
        }
        return builder.build();
    }

    private CallerContext caller() {
        return new CallerContext(CALLER_ID, properties.getCrypto().getTenantId(),
                Set.of("system", "crypto-admin"), "default", Map.of());
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }

    private record CipherContext(Spissatus spissatus, KeyId keyId, boolean owned) {
    }
}