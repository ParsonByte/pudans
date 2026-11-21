package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.Digests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;
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

    // 扩展初始化后读取主密钥并建立版本快照。
    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        parameterGroups.getServer().subscribe(
                server -> initialize(server.getMasterKey()),
                error -> log.warn("无法读取主密钥；令牌签名继续使用旧版哈希", error));
    }

    // 根据密钥指纹判断版本，轮换时自动递增。
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

    // 返回当前主密钥版本，供令牌校验使用。
    public int currentVersion() {
        return state.get().version();
    }

    // 判断是否已配置主密钥。
    public boolean hasMasterKey() {
        return StringUtils.hasText(state.get().key());
    }

    // 主密钥轮换覆盖 PAT 和刷新访问令牌；RSA JWT 仍使用现有解码器。
    public String hash(String secret) {
        return Digests.hmacSha256Hex(state.get().key(), secret);
    }

    // 按令牌版本选择哈希算法并做恒定时间比较。
    public boolean matches(String storedHash, String secret, Integer tokenVersion) {
        if (!StringUtils.hasText(storedHash) || secret == null) {
            return false;
        }
        var current = state.get();
        if (tokenVersion == null) {
            if (current.version() != 0) {
                return false;
            }
            return Digests.constantTimeEquals(storedHash, Digests.sha256Hex(secret));
        }
        if (tokenVersion != current.version()) {
            return false;
        }
        return Digests.constantTimeEquals(storedHash, Digests.hmacSha256Hex(current.key(), secret));
    }

    // 计算密钥指纹，空密钥使用固定标记。
    private static String fingerprint(String key) {
        return StringUtils.hasText(key) ? Digests.sha256Hex(key) : NO_KEY;
    }

    // 主密钥版本文件存放在工作目录的密钥子目录。
    private Path versionFile() {
        return pudansProperties.getWorkDir().resolve("keys").resolve(VERSION_FILE);
    }

    // 持久化版本与指纹，并尽量收紧文件权限。
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
