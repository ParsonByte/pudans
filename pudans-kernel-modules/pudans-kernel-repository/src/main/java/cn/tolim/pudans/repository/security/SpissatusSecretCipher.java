package cn.tolim.pudans.repository.security;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.spissatus4j.contract.algorithm.AlgorithmId;
import cn.tolim.spissatus4j.contract.algorithm.KeyUsage;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.contract.crypto.EncryptRequest;
import cn.tolim.spissatus4j.contract.envelope.BinaryEncoding;
import cn.tolim.spissatus4j.contract.envelope.Envelope;
import cn.tolim.spissatus4j.contract.envelope.EnvelopeFormat;
import cn.tolim.spissatus4j.contract.key.KeyDescriptor;
import cn.tolim.spissatus4j.contract.key.KeyGenerationRequest;
import cn.tolim.spissatus4j.contract.key.KeyId;
import cn.tolim.spissatus4j.contract.key.KeyState;
import cn.tolim.spissatus4j.contract.key.KeyType;
import cn.tolim.spissatus4j.contract.key.KeyVersion;
import cn.tolim.spissatus4j.contract.policy.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Storage secret encryption backed by the spissatus4j crypto facade
public final class SpissatusSecretCipher implements SecretCipher {

    public static final String PREFIX = "spissatus:v1:";

    private static final Logger LOGGER = LoggerFactory.getLogger(SpissatusSecretCipher.class);

    private final Spissatus spissatus;
    private final KeyId keyId;
    private final CallerContext caller;
    private final int keyBits;
    private final Object keyLock = new Object();
    private volatile boolean keyReady;

    public SpissatusSecretCipher(Spissatus spissatus, RepositoryProperties.Crypto properties) {
        if (spissatus == null) {
            throw new RepositoryException(RepositoryErrorCode.CRYPTO_UNAVAILABLE,
                    "Spissatus facade is required");
        }
        this.spissatus = spissatus;
        this.keyId = KeyId.of(properties.getKeyId());
        this.caller = new CallerContext(properties.getCallerId(), properties.getTenantId(),
                Set.of("system", "crypto-admin"), properties.getCallerEnvironment(), Map.of());
        this.keyBits = properties.getKeyBits();
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    "Plaintext secret must not be empty");
        }
        ensureKey();
        try {
            byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
            Envelope envelope = spissatus.encryptToEnvelope(EncryptRequest
                    .builder(AlgorithmId.AES_GCM, keyId, bytes, caller)
                    .build(), EnvelopeFormat.JSON, BinaryEncoding.BASE64);
            String encoded = spissatus.envelopeService()
                    .encodeText(envelope, EnvelopeFormat.JSON, BinaryEncoding.BASE64);
            return PREFIX + encoded;
        } catch (RuntimeException exception) {
            throw new RepositoryException(RepositoryErrorCode.CRYPTO_UNAVAILABLE,
                    "Storage secret encryption failed", exception);
        }
    }

    @Override
    public String decryptIfEncrypted(String value) {
        if (value == null || value.isEmpty() || !value.startsWith(PREFIX)) {
            return value;
        }
        try {
            String encoded = value.substring(PREFIX.length());
            byte[] envelopeBytes = Base64.getDecoder().decode(encoded);
            byte[] plaintext = spissatus.decryptEnvelope(envelopeBytes, caller);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new RepositoryException(RepositoryErrorCode.SECRET_DECRYPTION_FAILED,
                    "Storage secret decryption failed", exception);
        }
    }

    // Create the managed AES key once if it does not exist yet
    private void ensureKey() {
        if (keyReady) {
            return;
        }
        synchronized (keyLock) {
            if (keyReady) {
                return;
            }
            List<KeyDescriptor> descriptors = spissatus.keyManager().list(caller.tenantId(), caller);
            boolean active = descriptors.stream()
                    .anyMatch(descriptor -> descriptor.keyId().equals(keyId)
                            && descriptor.state() == KeyState.ACTIVE);
            if (!active) {
                LOGGER.info("Creating repository secret key {}", keyId.value());
                spissatus.keyManager().generate(KeyGenerationRequest
                        .builder(KeyType.AES, AlgorithmId.AES_GCM,
                                Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT))
                        .keyId(keyId)
                        .keyBits(keyBits)
                        .tenantId(caller.tenantId())
                        .build(), caller);
            }
            keyReady = true;
        }
    }

    // Expose the managed key version for diagnostics
    public KeyVersion currentVersion() {
        return spissatus.keyManager().describe(keyId, null, caller)
                .map(KeyDescriptor::version)
                .orElse(null);
    }
}