package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.properties.PudansProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParameterSettingSecretCipherTest {

    @Test
    void encryptsAndReusesTheStoredKey(@TempDir Path tempDir) {
        var properties = new PudansProperties();
        properties.setWorkDir(tempDir);
        var cipher = new ParameterSettingSecretCipher(properties);

        var encrypted = cipher.encrypt("super-secret");

        assertThat(encrypted).startsWith("enc:");
        assertThat(encrypted).doesNotContain("super-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("super-secret");
        assertThat(Files.exists(tempDir.resolve("keys").resolve("parameter-settings.key")))
                .isTrue();

        var restarted = new ParameterSettingSecretCipher(properties);
        assertThat(restarted.decrypt(encrypted)).isEqualTo("super-secret");
    }

    @Test
    void leavesPlainValuesUnchanged(@TempDir Path tempDir) {
        var properties = new PudansProperties();
        properties.setWorkDir(tempDir);
        var cipher = new ParameterSettingSecretCipher(properties);

        assertThat(cipher.decrypt("plain-value")).isEqualTo("plain-value");
        assertThat(cipher.isEncrypted("plain-value")).isFalse();
    }
}
