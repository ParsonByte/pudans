package cn.tolim.pudans.iam.identitysource.feishu.util;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeiShuEventDecryptUtilsTests {

    private static final String ENCRYPT_KEY = "pudans-feishu-encrypt-key";
    private static final int BLOCK_BYTES = 16;

    @Test
    void decryptsProtocolPayload() throws Exception {
        String plaintext = "{\"type\":\"event_callback\",\"token\":\"pudans-token\","
                + "\"event\":{\"user_id\":\"u-1\"}}";
        String payload = encryptPayload(plaintext);

        assertThat(FeiShuEventDecryptUtils.decrypt(ENCRYPT_KEY, payload)).isEqualTo(plaintext);
    }

    @Test
    void decryptsPayloadWithExactBlockLength() throws Exception {
        String plaintext = "0123456789abcdef";
        String payload = encryptPayload(plaintext);

        assertThat(FeiShuEventDecryptUtils.decrypt(ENCRYPT_KEY, payload)).isEqualTo(plaintext);
    }

    @Test
    void rejectsMalformedBase64() {
        assertThatThrownBy(() -> FeiShuEventDecryptUtils.decrypt(ENCRYPT_KEY, "not-base64!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTruncatedPayload() {
        String payload = Base64.getEncoder().encodeToString(new byte[8]);

        assertThatThrownBy(() -> FeiShuEventDecryptUtils.decrypt(ENCRYPT_KEY, payload))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    // Produces a Feishu-shaped payload with SHA-256 key derivation and an IV prefix
    private static String encryptPayload(String plaintext) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(ENCRYPT_KEY.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[BLOCK_BYTES];
        new SecureRandom().nextBytes(iv);
        byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
        int padding = BLOCK_BYTES - data.length % BLOCK_BYTES;
        byte[] padded = new byte[data.length + padding];
        System.arraycopy(data, 0, padded, 0, data.length);
        Arrays.fill(padded, data.length, padded.length, (byte) padding);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(padded);
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(payload);
    }
}