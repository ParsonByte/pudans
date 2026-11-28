package cn.tolim.pudans.notification.security;

import cn.tolim.pudans.notification.configuration.NotificationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSecretCipherTests {

    @Test
    void encryptsAndDecryptsProviderConfiguration() {
        NotificationSecretCipher cipher = new NotificationSecretCipher(
                new NotificationProperties());
        String plaintext = "{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"super-secret\"}";

        String encrypted = cipher.encrypt(plaintext);

        assertThat(encrypted).startsWith("spissatus:v1:");
        assertThat(encrypted).doesNotContain("super-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void rejectsMalformedCiphertext() {
        NotificationSecretCipher cipher = new NotificationSecretCipher(
                new NotificationProperties());

        assertThatThrownBy(() -> cipher.decrypt("spissatus:v1:broken"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leavesBlankValuesUntouched() {
        NotificationSecretCipher cipher = new NotificationSecretCipher(
                new NotificationProperties());

        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("")).isEmpty();
        assertThat(cipher.decrypt("")).isEmpty();
    }
}