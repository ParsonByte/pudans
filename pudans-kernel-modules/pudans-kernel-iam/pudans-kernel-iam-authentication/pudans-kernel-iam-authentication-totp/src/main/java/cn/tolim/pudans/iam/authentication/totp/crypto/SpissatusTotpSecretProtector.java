package cn.tolim.pudans.iam.authentication.totp.crypto;

import cn.tolim.pudans.iam.authentication.totp.config.TotpAuthenticationProperties;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpSecretUnavailableException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Encrypts TOTP secrets with the spissatus4j AEAD facade
@Slf4j
public class SpissatusTotpSecretProtector implements TotpSecretProtector, AutoCloseable {

    private static final AlgorithmId SECRET_ALGORITHM = AlgorithmId.AES_GCM;
    private static final Set<KeyUsage> KEY_USAGES = Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT);

    private final ObjectProvider<Spissatus> spissatusProvider;
    private final TotpAuthenticationProperties properties;
    private final CallerContext caller;
    private final KeyId keyId;

    private volatile Spissatus internalSpissatus;

    public SpissatusTotpSecretProtector(ObjectProvider<Spissatus> spissatusProvider,
                                        TotpAuthenticationProperties properties) {
        this.spissatusProvider = Objects.requireNonNull(spissatusProvider,
                "spissatusProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        TotpAuthenticationProperties.Crypto crypto = properties.getCrypto();
        this.caller = CallerContext.of(crypto.getCallerId(), crypto.getTenantId(),
                Set.of("system", "crypto-admin"));
        this.keyId = KeyId.of(crypto.getKeyId());
    }

    @Override
    public byte[] generateSecret(int length) {
        if (length < 10 || length > 64) {
            throw new IllegalArgumentException("TOTP secret length must be between 10 and 64 bytes");
        }
        return spissatus().crypto().randomBytes(length);
    }

    @Override
    public TotpSecretEnvelope protect(String userId, byte[] secret) {
        requireUser(userId);
        Objects.requireNonNull(secret, "secret must not be null");
        KeyId activeKeyId = ensureSecretKey();
        try {
            EncryptedData encrypted = spissatus().crypto().encrypt(EncryptRequest
                    .builder(SECRET_ALGORITHM, activeKeyId, secret, caller)
                    .aad(AAD.ofUtf8(userId))
                    .build());
            return new TotpSecretEnvelope(encrypted.algorithm().id(),
                    encrypted.keyId().value(), encrypted.keyVersion().value(),
                    Base64.getEncoder().encodeToString(encrypted.nonce().asBytes()),
                    Base64.getEncoder().encodeToString(encrypted.ciphertext()),
                    Base64.getEncoder().encodeToString(encrypted.tag()));
        } catch (RuntimeException exception) {
            log.error("Failed to encrypt TOTP secret for user {}", userId, exception);
            throw new TotpSecretUnavailableException(exception);
        }
    }

    @Override
    public byte[] unprotect(String userId, TotpSecretEnvelope envelope) {
        requireUser(userId);
        Objects.requireNonNull(envelope, "envelope must not be null");
        try {
            EncryptedData encrypted = EncryptedData.builder()
                    .algorithm(AlgorithmId.of(envelope.algorithm()))
                    .keyId(KeyId.of(envelope.keyId()))
                    .keyVersion(KeyVersion.of(envelope.keyVersion()))
                    .nonce(Nonce.of(Base64.getDecoder().decode(envelope.nonce())))
                    .ciphertext(Base64.getDecoder().decode(envelope.ciphertext()))
                    .tag(Base64.getDecoder().decode(envelope.tag()))
                    .aad(AAD.ofUtf8(userId))
                    .build();
            return spissatus().crypto().decrypt(DecryptRequest.from(encrypted, caller));
        } catch (RuntimeException exception) {
            log.error("Failed to decrypt TOTP secret for user {}", userId, exception);
            throw new TotpSecretUnavailableException(exception);
        }
    }

    @Override
    public void close() {
        Spissatus owned = internalSpissatus;
        if (owned != null) {
            owned.close();
            internalSpissatus = null;
        }
    }

    // Reuses an application provided facade, otherwise builds an internal one
    private Spissatus spissatus() {
        Spissatus provided = spissatusProvider.getIfAvailable();
        if (provided != null) {
            return provided;
        }
        Spissatus owned = internalSpissatus;
        if (owned == null) {
            synchronized (this) {
                owned = internalSpissatus;
                if (owned == null) {
                    owned = buildInternalSpissatus();
                    internalSpissatus = owned;
                }
            }
        }
        return owned;
    }

    // Generates the AES-GCM key on first use
    private KeyId ensureSecretKey() {
        Spissatus facade = spissatus();
        Optional<KeyDescriptor> descriptor = facade.keyManager()
                .describe(keyId, KeyVersion.first(), caller);
        if (descriptor.isPresent()) {
            return keyId;
        }
        synchronized (this) {
            descriptor = facade.keyManager().describe(keyId, KeyVersion.first(), caller);
            if (descriptor.isEmpty()) {
                facade.keyManager().generate(KeyGenerationRequest
                        .builder(KeyType.AES, SECRET_ALGORITHM, KEY_USAGES)
                        .keyId(keyId)
                        .tenantId(caller.tenantId())
                        .attributes(java.util.Map.of("purpose", "totp-secret-at-rest"))
                        .build(), caller);
                log.info("Generated spissatus key {} for TOTP secret protection", keyId);
            }
        }
        return keyId;
    }

    private Spissatus buildInternalSpissatus() {
        TotpAuthenticationProperties.Crypto crypto = properties.getCrypto();
        if (crypto.getKeyProvider() == null || "memory".equalsIgnoreCase(crypto.getKeyProvider())) {
            log.warn("TOTP secret encryption uses the spissatus in-memory key provider;"
                    + " secrets cannot be decrypted after a restart. Configure a file or"
                    + " keystore provider in production.");
        }
        SpissatusBuilder builder = SpissatusBuilders.builder()
                .discoverModules(true)
                .failClosed(true)
                .selfCheckOnStartup(true)
                .selfCheckFailureFailsStartup(true)
                .setting("spissatus.key.provider", crypto.getKeyProvider());
        putIfPresent(builder, "spissatus.key.file.directory", crypto.getFileDirectory());
        putIfPresent(builder, "spissatus.key.master.password", crypto.getMasterPassword());
        putIfPresent(builder, "spissatus.key.environment.prefix", crypto.getEnvironmentPrefix());
        putIfPresent(builder, "spissatus.key.keystore.path", crypto.getKeystorePath());
        putIfPresent(builder, "spissatus.key.keystore.password", crypto.getKeystorePassword());
        putIfPresent(builder, "spissatus.compliance.mode", crypto.getComplianceMode());
        return builder.build();
    }

    private static void putIfPresent(SpissatusBuilder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.setting(key, value);
        }
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("TOTP user ID must not be blank");
        }
    }
}