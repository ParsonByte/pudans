package cn.tolim.pudans.infra.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class KeyFileStore {

    private static final int KEY_LENGTH = 32;

    private static final String SECURE_PERMISSIONS = "rw-------";

    private KeyFileStore() {}

    // 密钥文件不存在时生成并以最小权限持久化。
    public static byte[] loadOrCreate(Path keyFile) {
        try {
            if (Files.exists(keyFile)) {
                return Base64.getDecoder().decode(Files.readString(keyFile).trim());
            }
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
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化密钥文件 " + keyFile, e);
        }
    }
}
