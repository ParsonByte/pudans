package cn.tolim.pudans.iam.authentication.totp.crypto;

// Protects TOTP secrets at rest using cn.tolim.spissatus4j
public interface TotpSecretProtector {

    // Generates cryptographically strong secret bytes
    byte[] generateSecret(int length);

    // Encrypts a TOTP secret and binds the ciphertext to the user
    TotpSecretEnvelope protect(String userId, byte[] secret);

    // Decrypts a TOTP secret previously protected for the same user
    byte[] unprotect(String userId, TotpSecretEnvelope envelope);
}