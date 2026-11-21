package cn.tolim.pudans.security.authentication.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.tolim.pudans.security.authentication.login.InvalidEncryptedMessageException;

class RsaLoginCipherTest {

    @TempDir
    Path tempDir;

    @Test
    void decryptsOaepWithSha256() throws Exception {
        var cipher = cipher();
        var encrypted = encrypt(cipher, "secret", new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));

        assertThat(new String(cipher.decrypt(encrypted).block(), UTF_8)).isEqualTo("secret");
    }

    @Test
    void decryptsOaepWithSha1Mgf1() throws Exception {
        var cipher = cipher();
        var encrypted = encrypt(cipher, "secret", new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));

        assertThat(new String(cipher.decrypt(encrypted).block(), UTF_8)).isEqualTo("secret");
    }

    @Test
    void decryptsLegacyPkcs1Messages() throws Exception {
        var cipher = cipher();
        var publicKey = publicKey(cipher);
        var encryptor = Cipher.getInstance(RsaLoginCipher.TRANSFORMATION);
        encryptor.init(Cipher.ENCRYPT_MODE, publicKey);
        var encrypted = encryptor.doFinal("secret".getBytes(UTF_8));

        assertThat(new String(cipher.decrypt(encrypted).block(), UTF_8)).isEqualTo("secret");
    }

    @Test
    void rejectsInvalidCiphertext() throws Exception {
        var cipher = cipher();

        assertThatThrownBy(() -> cipher.decrypt(new byte[512]).block())
                .isInstanceOf(InvalidEncryptedMessageException.class);
    }

    // 创建并初始化临时目录下的登录解密器。
    private RsaLoginCipher cipher() throws Exception {
        var cipher = new RsaLoginCipher(tempDir.resolve("keys"));
        cipher.afterPropertiesSet();
        return cipher;
    }

    // 读取公钥并构造客户端加密结果。
    private static byte[] encrypt(RsaLoginCipher cipher, String value, OAEPParameterSpec parameters)
            throws Exception {
        var encryptor = Cipher.getInstance(RsaLoginCipher.OAEP_TRANSFORMATION);
        encryptor.init(Cipher.ENCRYPT_MODE, publicKey(cipher), parameters);
        return encryptor.doFinal(value.getBytes(UTF_8));
    }

    // 将导出的 X.509 公钥还原为 RSA 公钥。
    private static PublicKey publicKey(RsaLoginCipher cipher) throws Exception {
        var encoded = cipher.readPublicKey().block();
        assertThat(encoded).isNotNull();
        return KeyFactory.getInstance(RsaLoginCipher.ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(encoded));
    }
}
