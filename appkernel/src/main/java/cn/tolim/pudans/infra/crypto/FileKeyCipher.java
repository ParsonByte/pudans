package cn.tolim.pudans.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;

@Slf4j
public final class FileKeyCipher {

    private static final String PREFIX = "enc:";

    private final BytesEncryptor encryptor;

    public FileKeyCipher(Path keyFile) {
        SecretKey key = new SecretKeySpec(KeyFileStore.loadOrCreate(keyFile), "AES");
        this.encryptor =
                new AesBytesEncryptor(key, KeyGenerators.secureRandom(32), AesBytesEncryptor.CipherAlgorithm.GCM);
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String rawValue) {
        var encrypted = encryptor.encrypt(rawValue.getBytes(StandardCharsets.UTF_8));
        return PREFIX + new String(Hex.encode(encrypted));
    }

    public String decrypt(String storedValue) {
        if (!isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            var decoded = Hex.decode(storedValue.substring(PREFIX.length()));
            return new String(encryptor.decrypt(decoded), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            log.warn("无法解密已存储的密钥");
            return storedValue;
        }
    }
}
