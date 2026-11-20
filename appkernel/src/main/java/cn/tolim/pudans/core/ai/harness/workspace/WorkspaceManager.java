package cn.tolim.pudans.core.ai.harness.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final WorkspaceSpec spec;

    private final Path root;

    public WorkspaceManager(WorkspaceSpec spec) {
        this.spec = Objects.requireNonNull(spec, "工作区配置不能为空。");
        this.root = spec.root();
    }

    public WorkspaceSpec spec() {
        return spec;
    }

    public Path root() {
        return root;
    }

    public Path skillsRoot() {
        return resolve(spec.skillsDirectory());
    }

    public void ensureDirectories() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(skillsRoot());
        } catch (IOException error) {
            throw new UncheckedIOException("无法创建工作区目录：" + root, error);
        }
    }

    public Path resolve(String relativePath) {
        if (relativePath == null) {
            throw new IllegalArgumentException("工作区相对路径不能为空。");
        }
        var value = relativePath.replace('\\', '/').strip();
        if (value.isEmpty() || ".".equals(value)) {
            return checked(root);
        }
        var relative = Path.of(value).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("工作区路径不能越界：" + relativePath);
        }
        return checked(root.resolve(relative));
    }

    public Optional<String> readText(String relativePath) {
        var path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new UncheckedIOException("无法读取工作区文件：" + path, error);
        }
    }

    public void writeText(String relativePath, String content) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("工作区相对路径不能为空。");
        }
        Path temporary = null;
        try {
            var target = resolve(relativePath);
            if (target.equals(root)) {
                throw new IllegalArgumentException("工作区文件路径不能为空。");
            }
            var parent = target.getParent();
            if (parent == null) {
                throw new IllegalArgumentException("工作区文件路径不能为空。");
            }
            Files.createDirectories(parent);
            target = resolve(relativePath);
            temporary = Files.createTempFile(parent, ".workspace-", ".tmp");
            Files.writeString(
                    temporary, content == null ? "" : content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException error) {
            throw new UncheckedIOException("无法写入工作区文件：" + relativePath, error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException error) {
                    log.debug("清理临时文件 {} 失败：{}", temporary, error.getMessage());
                }
            }
        }
    }

    private Path checked(Path candidate) {
        var normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("工作区路径不能越界：" + candidate);
        }
        if (!Files.exists(root)) {
            return normalized;
        }
        // 最近的存在祖先用于拦截经由符号链接逃逸工作区的路径
        var probe = normalized;
        while (probe != null && !Files.exists(probe)) {
            probe = probe.getParent();
        }
        if (probe == null) {
            throw new IllegalArgumentException("工作区路径不能越界：" + candidate);
        }
        var realRoot = realRoot();
        Path realProbe;
        try {
            realProbe = probe.toRealPath();
        } catch (IOException error) {
            throw new UncheckedIOException("无法解析工作区路径：" + probe, error);
        }
        if (!realProbe.startsWith(realRoot)) {
            throw new IllegalArgumentException("工作区路径不能越界：" + candidate);
        }
        return normalized;
    }

    private Path realRoot() {
        try {
            return root.toRealPath();
        } catch (IOException error) {
            throw new UncheckedIOException("无法解析工作区根目录：" + root, error);
        }
    }
}