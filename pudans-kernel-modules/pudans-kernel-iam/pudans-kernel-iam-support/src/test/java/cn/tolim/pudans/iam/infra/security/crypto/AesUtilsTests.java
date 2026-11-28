package cn.tolim.pudans.iam.infra.security.crypto;

import cn.tolim.pudans.iam.infra.util.AesUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesUtilsTests {

    private static final String PREFIX = "spissatus:aes-gcm:v1:";
    private static final String KEY = key(0);
    private static final String OTHER_KEY = key(31);

    @Test
    void encryptsAndDecryptsWithStaticApi() {
        String ciphertext = AesUtils.encrypt("pudans-secret", KEY);

        assertThat(ciphertext).startsWith(PREFIX);
        assertThat(ciphertext).doesNotContain("pudans-secret");
        assertThat(AesUtils.decrypt(ciphertext, KEY)).isEqualTo("pudans-secret");
    }

    @Test
    void encryptsAndDecryptsWithInstanceApi() {
        AesUtils utils = new AesUtils(KEY);

        String ciphertext = utils.encrypt("session-secret");

        assertThat(ciphertext).startsWith(PREFIX);
        assertThat(utils.decrypt(ciphertext)).isEqualTo("session-secret");
    }

    @Test
    void leavesBlankValuesUntouchedOnTheInstanceApi() {
        AesUtils utils = new AesUtils(KEY);

        assertThat(utils.encrypt(null)).isNull();
        assertThat(utils.decrypt(null)).isNull();
        assertThat(utils.encrypt("  ")).isNull();
        assertThat(utils.decrypt("  ")).isNull();
    }

    @Test
    void failsDecryptionWithTheWrongKey() {
        String ciphertext = AesUtils.encrypt("pudans-secret", KEY);

        assertThatThrownBy(() -> AesUtils.decrypt(ciphertext, OTHER_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES-GCM decryption failed");
    }

    @Test
    void failsDecryptionOfTamperedCiphertext() {
        String ciphertext = AesUtils.encrypt("pudans-secret", KEY);
        String[] parts = ciphertext.substring(PREFIX.length()).split(":", -1);
        byte[] tag = Base64.getDecoder().decode(parts[2]);
        tag[0] ^= 1;
        String tampered = PREFIX + parts[0] + ":" + parts[1] + ":"
                + Base64.getEncoder().encodeToString(tag);

        assertThatThrownBy(() -> AesUtils.decrypt(tampered, KEY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsupportedAndMalformedPayloads() {
        assertThatThrownBy(() -> AesUtils.decrypt("legacy-aes-payload", KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported AES ciphertext format");
        assertThatThrownBy(() -> AesUtils.decrypt(PREFIX + "broken", KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed AES ciphertext");
        assertThatThrownBy(() -> AesUtils.decrypt(PREFIX + "***:***:***", KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed AES ciphertext");
    }

    @Test
    void rejectsBlankAndNonBase64Keys() {
        assertThatThrownBy(() -> AesUtils.encrypt("value", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AES key must not be blank");
        assertThatThrownBy(() -> AesUtils.encrypt("value", "not-base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AES key is not valid Base64");
    }

    @Test
    void generatesDistinctUsableKeys() {
        String first = AesUtils.generateKey();
        String second = AesUtils.generateKey();

        assertThat(first).isNotEqualTo(second);
        assertThat(Base64.getDecoder().decode(first)).hasSize(16);
        assertThat(Base64.getDecoder().decode(second)).hasSize(16);
        assertThat(AesUtils.decrypt(AesUtils.encrypt("pudans-secret", first), first))
                .isEqualTo("pudans-secret");
    }

    private static String key(int seed) {
        byte[] value = new byte[16];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return Base64.getEncoder().encodeToString(value);
    }
}