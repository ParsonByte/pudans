package cn.tolim.pudans.core.ai.harness.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

    public List<WorkspaceEntry> list(String relativePath) {
        var path = resolve(relativePath);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("工作区目录不存在：" + relativePath);
        }
        try (var children = Files.list(path)) {
            return children
                    .map(child -> {
                        try {
                            return new WorkspaceEntry(
                                    child.getFileName().toString(),
                                    Files.isDirectory(child),
                                    Files.isRegularFile(child) ? Files.size(child) : 0L);
                        } catch (IOException error) {
                            throw new UncheckedIOException("无法读取工作区条目：" + child, error);
                        }
                    })
                    .sorted(Comparator.comparing(WorkspaceEntry::name))
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException("无法列出工作区目录：" + path, error);
        }
    }

    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    public void delete(String relativePath) {
        var path = resolve(relativePath);
        if (path.equals(root)) {
            throw new IllegalArgumentException("不能删除工作区根目录。");
        }
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException error) {
                    throw new UncheckedIOException("无法删除工作区文件：" + candidate, error);
                }
            });
        } catch (IOException error) {
            throw new UncheckedIOException("无法删除工作区文件：" + path, error);
        }
    }

    public void move(String from, String to) {
        var source = resolve(from);
        var target = resolve(to);
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("工作区文件不存在：" + from);
        }
        try {
            var parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new UncheckedIOException("无法移动工作区文件：" + from, error);
        }
    }

    public String snapshot() {
        var directory = snapshotDirectory();
        try {
            Files.createDirectories(directory);
            var id = "snapshot-" + UUID.randomUUID();
            var target = directory.resolve(id + ".zip");
            try (var output = new ZipOutputStream(Files.newOutputStream(target));
                    var paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> !path.startsWith(directory))
                        .forEach(path -> writeEntry(output, path));
            }
            return id;
        } catch (IOException error) {
            throw new UncheckedIOException("无法创建工作区快照。", error);
        }
    }

    public List<String> snapshots() {
        var directory = snapshotDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .map(path -> path.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - 4))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException("无法读取工作区快照。", error);
        }
    }

    public void restore(String snapshotId) {
        var zip = snapshotPath(snapshotId);
        if (!Files.isRegularFile(zip)) {
            throw new IllegalArgumentException("工作区快照不存在：" + snapshotId);
        }
        try (var input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(resolve(entry.getName()));
                    continue;
                }
                var target = resolve(entry.getName());
                var parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                input.closeEntry();
            }
        } catch (IOException error) {
            throw new UncheckedIOException("无法恢复工作区快照：" + snapshotId, error);
        }
    }

    private Path snapshotDirectory() {
        return resolve(".snapshots");
    }

    private Path snapshotPath(String snapshotId) {
        if (snapshotId == null || !snapshotId.matches("snapshot-[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("工作区快照标识不合法：" + snapshotId);
        }
        return snapshotDirectory().resolve(snapshotId + ".zip");
    }

    private void writeEntry(ZipOutputStream output, Path path) {
        try {
            var entryName = root.relativize(path).toString().replace('\\', '/');
            output.putNextEntry(new ZipEntry(entryName));
            Files.copy(path, output);
            output.closeEntry();
        } catch (IOException error) {
            throw new UncheckedIOException("无法写入工作区快照条目：" + path, error);
        }
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

    public record WorkspaceEntry(String name, boolean directory, long size) {}

    private Path realRoot() {
        try {
            return root.toRealPath();
        } catch (IOException error) {
            throw new UncheckedIOException("无法解析工作区根目录：" + root, error);
        }
    }
}