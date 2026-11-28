package cn.tolim.pudans.iam.infra.util;

import cn.tolim.pudans.iam.infra.security.crypto.spissatus.SpissatusKeyedSupport;
import cn.tolim.spissatus4j.contract.algorithm.AlgorithmId;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.contract.crypto.AAD;
import cn.tolim.spissatus4j.contract.crypto.DecryptRequest;
import cn.tolim.spissatus4j.contract.crypto.EncryptRequest;
import cn.tolim.spissatus4j.contract.crypto.EncryptedData;
import cn.tolim.spissatus4j.contract.crypto.Nonce;
import cn.tolim.spissatus4j.contract.key.KeyId;
import cn.tolim.spissatus4j.contract.key.KeyVersion;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;

// AES-GCM encryption and decryption backed by the spissatus4j facade
public class AesUtils {

    private static final String PREFIX = "spissatus:aes-gcm:v1:";
    private static final String SEPARATOR = ":";
    private static final String AAD_CONTEXT = "pudans-iam-aes-utils";
    private static final int PART_COUNT = 3;

    private final String key;

    private volatile KeyHolder holder;

    public AesUtils(String key) {
        this.key = key;
    }

    public static String encrypt(String value, String key) {
        return withHolder(key, current -> encrypt(value, current));
    }

    public static String decrypt(String value, String key) {
        return withHolder(key, current -> decrypt(value, current));
    }

    public static String generateKey() {
        return Base64.getEncoder().encodeToString(SpissatusKeyedSupport.randomKeyBytes());
    }

    public String encrypt(String value) {
        return value == null || value.isBlank() ? null : encrypt(value, holder());
    }

    public String decrypt(String value) {
        return value == null || value.isBlank() ? null : decrypt(value, holder());
    }

    private static String encrypt(String value, KeyHolder current) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            EncryptedData encrypted = current.facade().crypto().encrypt(EncryptRequest
                    .builder(AlgorithmId.AES_GCM, current.keyId(),
                            value.getBytes(StandardCharsets.UTF_8), SpissatusKeyedSupport.caller())
                    .aad(AAD.ofUtf8(AAD_CONTEXT))
                    .build());
            return PREFIX + encode(encrypted.nonce().asBytes()) + SEPARATOR
                    + encode(encrypted.ciphertext()) + SEPARATOR + encode(encrypted.tag());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AES-GCM encryption failed", exception);
        }
    }

    private static String decrypt(String value, KeyHolder current) {
        Objects.requireNonNull(value, "value must not be null");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported AES ciphertext format");
        }
        String[] parts = value.substring(PREFIX.length()).split(SEPARATOR, -1);
        if (parts.length != PART_COUNT) {
            throw new IllegalArgumentException("Malformed AES ciphertext");
        }
        byte[] nonce;
        byte[] ciphertext;
        byte[] tag;
        try {
            nonce = Base64.getDecoder().decode(parts[0]);
            ciphertext = Base64.getDecoder().decode(parts[1]);
            tag = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed AES ciphertext", exception);
        }
        try {
            byte[] plaintext = current.facade().crypto().decrypt(DecryptRequest
                    .builder(AlgorithmId.AES_GCM, current.keyId(), ciphertext,
                            SpissatusKeyedSupport.caller())
                    .keyVersion(KeyVersion.first())
                    .nonce(Nonce.of(nonce))
                    .tag(tag)
                    .aad(AAD.ofUtf8(AAD_CONTEXT))
                    .build());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AES-GCM decryption failed", exception);
        }
    }

    private KeyHolder holder() {
        KeyHolder current = holder;
        if (current == null) {
            synchronized (this) {
                current = holder;
                if (current == null) {
                    current = createHolder(key);
                    holder = current;
                }
            }
        }
        return current;
    }

    private static <T> T withHolder(String key, Function<KeyHolder, T> action) {
        KeyHolder current = createHolder(key);
        try {
            return action.apply(current);
        } finally {
            current.facade().close();
        }
    }

    private static KeyHolder createHolder(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("AES key must not be blank");
        }
        byte[] rawKey;
        try {
            rawKey = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AES key is not valid Base64", exception);
        }
        Spissatus facade = SpissatusKeyedSupport.createFacade();
        try {
            KeyId keyId = SpissatusKeyedSupport.importAesKey(facade, rawKey);
            return new KeyHolder(facade, keyId);
        } catch (RuntimeException exception) {
            facade.close();
            throw exception;
        } finally {
            Arrays.fill(rawKey, (byte) 0);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private record KeyHolder(Spissatus facade, KeyId keyId) {
    }
}