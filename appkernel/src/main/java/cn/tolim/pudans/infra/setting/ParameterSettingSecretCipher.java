package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.properties.PudansProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParameterSettingSecretCipher {

    private static final String PREFIX = "enc:";

    private static final String KEY_FILE = "parameter-settings.key";

    private static final String SECURE_PERMISSIONS = "rw-------";

    private static final int KEY_LENGTH = 32;

    private final BytesEncryptor encryptor;

    public ParameterSettingSecretCipher(PudansProperties properties) {
        this.encryptor = loadOrCreate(properties.getWorkDir().resolve("keys").resolve(KEY_FILE));
    }

    private static BytesEncryptor loadOrCreate(Path keyFile) {
        try {
            var keyBytes = Files.exists(keyFile)
                    ? Base64.getDecoder().decode(Files.readString(keyFile))
                    : createKey(keyFile);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            return new AesBytesEncryptor(key, KeyGenerators.secureRandom(32), AesBytesEncryptor.CipherAlgorithm.GCM);
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化参数设置密钥加密器", e);
        }
    }

    private static byte[] createKey(Path keyFile) throws IOException {
        var parent = keyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var keyBytes = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(keyBytes);
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(keyBytes));
        try {
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString(SECURE_PERMISSIONS));
        } catch (UnsupportedOperationException e) {
            log.debug("{} 不支持 POSIX 权限", keyFile);
        }
        return keyBytes;
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
            log.warn("无法解密已存储的参数设置密钥");
            return storedValue;
        }
    }
}
