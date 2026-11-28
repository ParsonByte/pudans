package cn.tolim.pudans.iam.infra.security.crypto;

import cn.tolim.pudans.iam.infra.util.RsaUtils;
import cn.tolim.spissatus4j.contract.exception.CryptoException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaUtilsTests {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static RSAPrivateKey otherPrivateKey;

    @BeforeAll
    static void generateKeyPairs() {
        RsaUtils.RsaResult keys = RsaUtils.getKeys(2048);
        publicKey = (RSAPublicKey) keys.getPublicKey();
        privateKey = (RSAPrivateKey) keys.getPrivateKey();
        otherPrivateKey = (RSAPrivateKey) RsaUtils.getKeys(2048).getPrivateKey();
    }

    @Test
    void encryptsWithPublicKeyAndDecryptsWithPrivateKey() {
        byte[] plaintext = "pudans-secret".getBytes(StandardCharsets.UTF_8);
        byte[] encodedPlaintext = Base64.getEncoder().encode(plaintext);

        String ciphertext = RsaUtils.encrypt(publicKey, encodedPlaintext);

        assertThat(ciphertext).isNotBlank();
        assertThat(ciphertext).doesNotContain("pudans-secret");
        assertThat(RsaUtils.decrypt(privateKey, Base64.getDecoder().decode(ciphertext)))
                .isEqualTo("pudans-secret");
    }

    @Test
    void failsDecryptionWithTheWrongPrivateKey() {
        byte[] encodedPlaintext = Base64.getEncoder()
                .encode("pudans-secret".getBytes(StandardCharsets.UTF_8));
        String ciphertext = RsaUtils.encrypt(publicKey, encodedPlaintext);

        assertThatThrownBy(() -> RsaUtils.decrypt(otherPrivateKey,
                Base64.getDecoder().decode(ciphertext)))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void failsDecryptionOfTamperedCiphertext() {
        byte[] encodedPlaintext = Base64.getEncoder()
                .encode("pudans-secret".getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = Base64.getDecoder().decode(RsaUtils.encrypt(publicKey, encodedPlaintext));
        ciphertext[ciphertext.length - 1] ^= 1;

        assertThatThrownBy(() -> RsaUtils.decrypt(privateKey, ciphertext))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void rejectsPrivateKeyEncryptionAndPublicKeyDecryption() {
        byte[] data = "pudans-secret".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> RsaUtils.encrypt(privateKey, data))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> RsaUtils.decrypt(publicKey, data))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void loadsPemKeysForFacadeOperations() throws Exception {
        RSAPublicKey loadedPublic = RsaUtils.loadPublicKey(pem("PUBLIC KEY",
                publicKey.getEncoded()));
        RSAPrivateKey loadedPrivate = RsaUtils.loadPrivateKey(pem("PRIVATE KEY",
                privateKey.getEncoded()));
        byte[] encodedPlaintext = Base64.getEncoder()
                .encode("pem-secret".getBytes(StandardCharsets.UTF_8));

        String ciphertext = RsaUtils.encrypt(loadedPublic, encodedPlaintext);

        assertThat(RsaUtils.decrypt(loadedPrivate, Base64.getDecoder().decode(ciphertext)))
                .isEqualTo("pem-secret");
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded)
                + "\n-----END " + label + "-----";
    }
}