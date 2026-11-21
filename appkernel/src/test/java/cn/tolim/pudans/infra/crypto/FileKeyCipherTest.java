package cn.tolim.pudans.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileKeyCipherTest {

    // 加密后的值必须能用同一个密钥文件还原。
    @Test
    void encryptsAndDecryptsWithTheSameKeyFile(@TempDir Path workDir) {
        var cipher = new FileKeyCipher(workDir.resolve("keys/file.key"));
        var encrypted = cipher.encrypt("secret");

        assertThat(encrypted).startsWith("enc:");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("secret");
        assertThat(new FileKeyCipher(workDir.resolve("keys/file.key")).decrypt(encrypted))
                .isEqualTo("secret");
    }

    // 未加密的历史值保持原样，避免迁移期数据不可读。
    @Test
    void keepsPlainValuesUntouched(@TempDir Path workDir) {
        var cipher = new FileKeyCipher(workDir.resolve("keys/file.key"));

        assertThat(cipher.decrypt("plain")).isEqualTo("plain");
    }

    // 密文损坏时必须显式失败，不能把密文当明文继续使用。
    @Test
    void rejectsCorruptedCiphertext(@TempDir Path workDir) {
        var cipher = new FileKeyCipher(workDir.resolve("keys/file.key"));

        assertThatThrownBy(() -> cipher.decrypt("enc:not-hex"))
                .isInstanceOf(RuntimeException.class);
    }
}
