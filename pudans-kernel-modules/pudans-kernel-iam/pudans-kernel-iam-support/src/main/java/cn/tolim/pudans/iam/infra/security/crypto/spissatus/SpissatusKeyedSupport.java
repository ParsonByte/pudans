package cn.tolim.pudans.iam.infra.security.crypto.spissatus;

import cn.tolim.spissatus4j.contract.algorithm.AlgorithmId;
import cn.tolim.spissatus4j.contract.algorithm.KeyUsage;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.contract.crypto.DigestRequest;
import cn.tolim.spissatus4j.contract.key.ImportFormat;
import cn.tolim.spissatus4j.contract.key.KeyDescriptor;
import cn.tolim.spissatus4j.contract.key.KeyId;
import cn.tolim.spissatus4j.contract.key.KeyImportRequest;
import cn.tolim.spissatus4j.contract.key.KeyType;
import cn.tolim.spissatus4j.contract.key.KeyVersion;
import cn.tolim.spissatus4j.contract.policy.CallerContext;
import cn.tolim.spissatus4j.core.builder.SpissatusBuilders;

import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Creates isolated spissatus4j facades for externally supplied key material
public final class SpissatusKeyedSupport {

    public static final String CALLER_ID = "pudans-iam";
    public static final String TENANT_ID = "pudans";

    private static final CallerContext CALLER = CallerContext.of(CALLER_ID, TENANT_ID,
            Set.of("system", "crypto-admin"));
    private static final String KEY_ID_PREFIX = "pudans-key-";
    private static final int KEY_ID_DIGEST_BYTES = 12;
    private static final int RANDOM_KEY_BYTES = 16;
    private static final Object RANDOM_FACADE_LOCK = new Object();

    private static volatile Spissatus randomFacade;

    private SpissatusKeyedSupport() {
    }

    public static CallerContext caller() {
        return CALLER;
    }

    // Builds a memory-backed facade without a startup self-check so callers control the cost
    public static Spissatus createFacade() {
        return SpissatusBuilders.builder()
                .discoverModules(true)
                .failClosed(true)
                .selfCheckOnStartup(false)
                .selfCheckFailureFailsStartup(false)
                .setting("spissatus.key.provider", "memory")
                .build();
    }

    // Generates AES-128 key material through the shared random-only facade
    public static byte[] randomKeyBytes() {
        return randomFacade().crypto().randomBytes(RANDOM_KEY_BYTES);
    }

    // Imports a raw AES key and activates its first version
    public static KeyId importAesKey(Spissatus facade, byte[] rawKey) {
        Objects.requireNonNull(facade, "facade must not be null");
        Objects.requireNonNull(rawKey, "rawKey must not be null");
        return importKey(facade, rawKey, KeyType.AES, AlgorithmId.AES_GCM,
                Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT), ImportFormat.RAW);
    }

    // Imports an X.509 RSA public key and activates its first version
    public static KeyId importRsaPublicKey(Spissatus facade, byte[] x509) {
        Objects.requireNonNull(facade, "facade must not be null");
        Objects.requireNonNull(x509, "x509 must not be null");
        return importKey(facade, x509, KeyType.RSA, AlgorithmId.RSA_OAEP,
                Set.of(KeyUsage.ENCRYPT), ImportFormat.X509);
    }

    // Imports a PKCS#8 RSA private key and activates its first version
    public static KeyId importRsaPrivateKey(Spissatus facade, byte[] pkcs8) {
        Objects.requireNonNull(facade, "facade must not be null");
        Objects.requireNonNull(pkcs8, "pkcs8 must not be null");
        return importKey(facade, pkcs8, KeyType.RSA, AlgorithmId.RSA_OAEP,
                Set.of(KeyUsage.DECRYPT), ImportFormat.PKCS8);
    }

    private static KeyId importKey(Spissatus facade, byte[] material, KeyType type,
                                   AlgorithmId algorithm, Set<KeyUsage> usages,
                                   ImportFormat format) {
        KeyId keyId = fingerprint(facade, material);
        synchronized (facade) {
            Optional<KeyDescriptor> existing = facade.keyManager()
                    .describe(keyId, KeyVersion.first(), CALLER);
            if (existing.isEmpty()) {
                facade.keyManager().importKey(KeyImportRequest
                        .builder(keyId, format, material, type, algorithm, usages)
                        .tenantId(TENANT_ID)
                        .build(), CALLER);
                facade.keyManager().activate(keyId, KeyVersion.first(), CALLER);
            } else if (!existing.get().state().canProtect()) {
                facade.keyManager().activate(keyId, KeyVersion.first(), CALLER);
            }
        }
        return keyId;
    }

    // Derives a deterministic key identifier from the supplied material
    private static KeyId fingerprint(Spissatus facade, byte[] material) {
        byte[] digest = facade.crypto().digest(
                new DigestRequest(AlgorithmId.SHA_256, material, CALLER));
        return KeyId.of(KEY_ID_PREFIX
                + HexFormat.of().formatHex(digest, 0, KEY_ID_DIGEST_BYTES));
    }

    private static Spissatus randomFacade() {
        Spissatus current = randomFacade;
        if (current == null) {
            synchronized (RANDOM_FACADE_LOCK) {
                current = randomFacade;
                if (current == null) {
                    current = createFacade();
                    randomFacade = current;
                }
            }
        }
        return current;
    }
}