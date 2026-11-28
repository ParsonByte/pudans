package cn.tolim.pudans.repository.security;

import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.error.RepositoryException;

// Fallback cipher used when no spissatus4j facade is available
public final class PlaintextSecretCipher implements SecretCipher {

    public static final String PREFIX = "spissatus:v1:";

    @Override
    public String encrypt(String plaintext) {
        throw new RepositoryException(RepositoryErrorCode.CRYPTO_UNAVAILABLE,
                "Encryption requires the spissatus4j crypto facade");
    }

    @Override
    public String decryptIfEncrypted(String value) {
        if (value != null && value.startsWith(PREFIX)) {
            throw new RepositoryException(RepositoryErrorCode.CRYPTO_UNAVAILABLE,
                    "Encrypted storage secrets require the spissatus4j crypto facade");
        }
        return value;
    }
}