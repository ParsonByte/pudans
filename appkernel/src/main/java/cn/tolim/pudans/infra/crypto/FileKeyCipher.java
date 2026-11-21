package cn.tolim.pudans.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;

public final class FileKeyCipher {

    private static final String PREFIX = "enc:";

    private final BytesEncryptor encryptor;

    // 从密钥文件加载或创建 AES-GCM 加密器。
    public FileKeyCipher(Path keyFile) {
        SecretKey key = new SecretKeySpec(KeyFileStore.loadOrCreate(keyFile), "AES");
        this.encryptor =
                new AesBytesEncryptor(key, KeyGenerators.secureRandom(32), AesBytesEncryptor.CipherAlgorithm.GCM);
    }

    // 通过前缀判断值是否已加密。
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    // 加密后附加前缀，便于识别存储格式。
    public String encrypt(String rawValue) {
        var encrypted = encryptor.encrypt(rawValue.getBytes(StandardCharsets.UTF_8));
        return PREFIX + new String(Hex.encode(encrypted));
    }

    // 未加密值原样返回，兼容历史数据。
    public String decrypt(String storedValue) {
        if (!isEncrypted(storedValue)) {
            return storedValue;
        }
        var decoded = Hex.decode(storedValue.substring(PREFIX.length()));
        return new String(encryptor.decrypt(decoded), StandardCharsets.UTF_8);
    }
}
