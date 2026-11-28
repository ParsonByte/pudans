package cn.tolim.pudans.iam.infra.util;

import cn.tolim.pudans.iam.infra.security.crypto.spissatus.SpissatusKeyedSupport;
import cn.tolim.spissatus4j.contract.algorithm.AlgorithmId;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.contract.crypto.DecryptRequest;
import cn.tolim.spissatus4j.contract.crypto.EncryptRequest;
import cn.tolim.spissatus4j.contract.crypto.EncryptedData;
import cn.tolim.spissatus4j.contract.key.KeyId;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

// RSA-OAEP encryption and decryption backed by the spissatus4j facade
public final class RsaUtils {
    public static final String PUBLIC_KEY = "PUBLIC_KEY";
    public static final String PRIVATE_KEY = "PRIVATE_KEY";
    private static final String ALGORITHM = "RSA";
    private static final int DEFAULT_KEY_SIZE = 2048;

    private RsaUtils() {
    }

    public static String encrypt(RSAPublicKey publicKey, byte[] data) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Spissatus facade = SpissatusKeyedSupport.createFacade();
        try {
            KeyId keyId = SpissatusKeyedSupport.importRsaPublicKey(facade,
                    publicKey.getEncoded());
            EncryptedData encrypted = facade.crypto().encrypt(EncryptRequest
                    .builder(AlgorithmId.RSA_OAEP, keyId, data, SpissatusKeyedSupport.caller())
                    .build());
            return Base64.getEncoder().encodeToString(encrypted.ciphertext());
        } finally {
            facade.close();
        }
    }

    public static String encrypt(RSAPrivateKey privateKey, byte[] data) {
        throw unsupportedPrivateKeyEncryption();
    }

    public static String decrypt(RSAPublicKey publicKey, byte[] data) {
        throw unsupportedPrivateKeyEncryption();
    }

    public static String decrypt(RSAPrivateKey privateKey, byte[] data) {
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Spissatus facade = SpissatusKeyedSupport.createFacade();
        try {
            KeyId keyId = SpissatusKeyedSupport.importRsaPrivateKey(facade,
                    privateKey.getEncoded());
            byte[] plaintext = facade.crypto().decrypt(DecryptRequest
                    .builder(AlgorithmId.RSA_OAEP, keyId, data, SpissatusKeyedSupport.caller())
                    .build());
            return new String(Base64.getDecoder().decode(plaintext), StandardCharsets.UTF_8);
        } finally {
            facade.close();
        }
    }

    public static RSAPrivateKey loadPrivateKey(String value) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(keyCleanup(value));
        return (RSAPrivateKey) KeyFactory.getInstance(ALGORITHM)
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    public static RSAPublicKey loadPublicKey(String value) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(keyCleanup(value));
        return (RSAPublicKey) KeyFactory.getInstance(ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(decoded));
    }

    // Key pair generation stays on the JDK because spissatus4j never exports private key material
    public static RsaResult getKeys() {
        return getKeys(DEFAULT_KEY_SIZE);
    }

    public static RsaResult getKeys(int keySize) {
        if (keySize < DEFAULT_KEY_SIZE) {
            throw new IllegalArgumentException("RSA 密钥长度至少为 2048 位");
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(keySize, new SecureRandom());
            var pair = generator.generateKeyPair();
            return new RsaResult(pair.getPrivate(), pair.getPublic());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("RSA 密钥生成失败", exception);
        }
    }

    public static String base64ToStr(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    public static byte[] strToBase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static UnsupportedOperationException unsupportedPrivateKeyEncryption() {
        return new UnsupportedOperationException(
                "RSA private-key encryption and public-key decryption are not supported;"
                        + " use a spissatus4j signature operation instead");
    }

    private static String keyCleanup(String value) {
        return value.replaceAll("-----BEGIN [^-]+-----", "").replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
    }

    public static class RsaResult {
        private PrivateKey privateKey;
        private PublicKey publicKey;

        public RsaResult(PrivateKey privateKey, PublicKey publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public PrivateKey getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(PublicKey publicKey) {
            this.publicKey = publicKey;
        }
    }
}