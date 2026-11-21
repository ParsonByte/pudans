package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.properties.PudansProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class MasterKeys {

    private static final String VERSION_FILE = "master-key.version";

    private static final String NO_KEY = "none";

    private final ParameterGroups parameterGroups;

    private final PudansProperties pudansProperties;

    private final AtomicReference<State> state = new AtomicReference<>(new State("", 0));

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        parameterGroups.getServer().subscribe(
                server -> initialize(server.getMasterKey()),
                error -> log.warn("无法读取主密钥；令牌签名继续使用旧版哈希", error));
    }

    void initialize(String masterKey) {
        var key = masterKey == null ? "" : masterKey;
        var fingerprint = fingerprint(key);
        var versionFile = versionFile();
        var version = 0;
        try {
            if (Files.exists(versionFile)) {
                var parts = Files.readString(versionFile).trim().split(":", 2);
                if (parts.length == 2) {
                    var storedVersion = Integer.parseInt(parts[0]);
                    if (parts[1].equals(fingerprint)) {
                        version = storedVersion;
                    } else {
                        version = storedVersion + 1;
                    }
                }
            }
            writeVersion(versionFile, version, fingerprint);
        } catch (IOException | NumberFormatException e) {
            log.warn("无法读取主密钥版本文件，使用版本 0", e);
        }
        state.set(new State(key, version));
        log.info("已捕获主密钥快照，版本 {}", version);
    }

    public int currentVersion() {
        return state.get().version();
    }

    public boolean hasMasterKey() {
        return StringUtils.hasText(state.get().key());
    }

    // 主密钥轮换覆盖 PAT 和刷新访问令牌；RSA JWT 仍使用现有解码器。
    public String hash(String secret) {
        return hash(secret, state.get().key());
    }

    public boolean matches(String storedHash, String secret, Integer tokenVersion) {
        if (!StringUtils.hasText(storedHash) || secret == null) {
            return false;
        }
        var current = state.get();
        if (tokenVersion == null) {
            if (current.version() != 0) {
                return false;
            }
            return constantTimeEquals(storedHash, sha256(secret));
        }
        if (tokenVersion != current.version()) {
            return false;
        }
        return constantTimeEquals(storedHash, hash(secret, current.key()));
    }

    private static String hash(String secret, String key) {
        if (!StringUtils.hasText(key)) {
            return sha256(secret);
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法使用主密钥进行签名", e);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String fingerprint(String key) {
        return StringUtils.hasText(key) ? sha256(key) : NO_KEY;
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private Path versionFile() {
        return pudansProperties.getWorkDir().resolve("keys").resolve(VERSION_FILE);
    }

    private static void writeVersion(Path file, int version, String fingerprint) throws IOException {
        var parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, version + ":" + fingerprint);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            log.debug("{} 不支持 POSIX 权限", file);
        }
    }

    private record State(String key, int version) {}
}
