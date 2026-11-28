package cn.tolim.pudans.iam.authentication.totp.crypto;

// Spissatus encrypted TOTP secret in a storable form
public record TotpSecretEnvelope(String algorithm, String keyId, long keyVersion, String nonce,
                                 String ciphertext, String tag) {

    public TotpSecretEnvelope {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Secret envelope algorithm must not be blank");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Secret envelope key ID must not be blank");
        }
        if (keyVersion < 1) {
            throw new IllegalArgumentException("Secret envelope key version must be positive");
        }
        if (nonce == null || ciphertext == null || tag == null) {
            throw new IllegalArgumentException("Secret envelope components must not be null");
        }
    }
}