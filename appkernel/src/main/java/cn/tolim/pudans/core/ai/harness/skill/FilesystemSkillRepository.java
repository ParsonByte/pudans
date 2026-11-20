package cn.tolim.pudans.core.ai.harness.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesystemSkillRepository implements SkillRepository {

    private static final Logger log = LoggerFactory.getLogger(FilesystemSkillRepository.class);

    private final Path baseDir;

    private final String source;

    private final boolean writable;

    public FilesystemSkillRepository(Path baseDir) {
        this(baseDir, "filesystem", false);
    }

    public FilesystemSkillRepository(Path baseDir, String source, boolean writable) {
        if (baseDir == null) {
            throw new IllegalArgumentException("技能目录不能为空。");
        }
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.source = SkillFiles.sourceOrDefault(source, "filesystem");
        this.writable = writable;
    }

    @Override
    public List<SkillEntry> list() {
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }
        try (var directories = Files.list(baseDir)) {
            return directories
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> SkillFiles.isVisible(path.getFileName().toString()))
                    .sorted()
                    .map(this::loadEntry)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException("无法列出技能目录：" + baseDir, error);
        }
    }

    @Override
    public void save(SkillEntry entry) {
        if (!writable) {
            throw new UnsupportedOperationException("技能仓库是只读的。");
        }
        if (entry == null) {
            throw new IllegalArgumentException("技能不能为空。");
        }
        var target = skillDirectory(entry.name());
        write(target.resolve(SkillFiles.SKILL_FILE), entry.markdown().getBytes(StandardCharsets.UTF_8));
        for (var relative : entry.resources().list()) {
            var content = entry.resources().readBinary(relative);
            if (content.isEmpty()) {
                continue;
            }
            write(SkillFiles.resolve(target, relative), content.get());
        }
    }

    @Override
    public boolean archive(String name) {
        if (!writable) {
            throw new UnsupportedOperationException("技能仓库是只读的。");
        }
        var skillDir = skillDirectory(name);
        if (!Files.isDirectory(skillDir, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        var archiveRoot = baseDir.resolve(".archive");
        var destination = archiveRoot.resolve(name + "-" + Instant.now().toEpochMilli());
        // 归档而非删除，保留技能历史
        try {
            Files.createDirectories(archiveRoot);
            Files.move(skillDir, destination);
            return true;
        } catch (IOException error) {
            throw new UncheckedIOException("无法归档技能：" + name, error);
        }
    }

    private Optional<SkillEntry> loadEntry(Path directory) {
        var skillFile = directory.resolve(SkillFiles.SKILL_FILE);
        if (!Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            var content = Files.readString(skillFile, StandardCharsets.UTF_8);
            return Optional.of(SkillEntry.fromMarkdown(
                    content,
                    directory.getFileName().toString(),
                    source,
                    new PathSkillResources(directory),
                    directory));
        } catch (IOException error) {
            log.warn("读取技能文件 {} 失败：{}", skillFile, error.getMessage());
            return Optional.empty();
        } catch (RuntimeException error) {
            log.warn("解析技能文件 {} 失败：{}", skillFile, error.getMessage());
            return Optional.empty();
        }
    }

    private Path skillDirectory(String name) {
        SkillFiles.validateName(name);
        var directory = baseDir.resolve(name).normalize();
        if (!directory.startsWith(baseDir)) {
            throw new IllegalArgumentException("技能目录不能越界：" + name);
        }
        if (Files.exists(directory)) {
            try {
                if (!directory.toRealPath().startsWith(baseDir.toRealPath())) {
                    throw new IllegalArgumentException("技能目录不能越界：" + name);
                }
            } catch (IOException error) {
                throw new UncheckedIOException("无法解析技能目录：" + directory, error);
            }
        }
        return directory;
    }

    private static void write(Path target, byte[] content) {
        var parent = target.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("技能文件路径不能为空。");
        }
        try {
            Files.createDirectories(parent);
            Files.write(target, content);
        } catch (IOException error) {
            throw new UncheckedIOException("无法写入技能文件：" + target, error);
        }
    }

    private static final class PathSkillResources implements SkillResources {

        private final Path root;

        PathSkillResources(Path root) {
            this.root = root;
        }

        @Override
        public Optional<String> read(String relativePath) {
            try {
                var path = SkillFiles.resolve(root, relativePath);
                if (!Files.isRegularFile(path)) {
                    return Optional.empty();
                }
                return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException error) {
                log.debug("读取技能资源 {} 失败：{}", relativePath, error.getMessage());
                return Optional.empty();
            }
        }

        @Override
        public Optional<byte[]> readBinary(String relativePath) {
            try {
                var path = SkillFiles.resolve(root, relativePath);
                if (!Files.isRegularFile(path)) {
                    return Optional.empty();
                }
                return Optional.of(Files.readAllBytes(path));
            } catch (IOException | RuntimeException error) {
                log.debug("读取技能资源 {} 失败：{}", relativePath, error.getMessage());
                return Optional.empty();
            }
        }

        @Override
        public List<String> list() {
            if (!Files.isDirectory(root)) {
                return List.of();
            }
            try (var files = Files.walk(root)) {
                return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .map(path -> SkillFiles.relative(root, path))
                        .filter(relative -> !SkillFiles.SKILL_FILE.equals(relative))
                        .sorted()
                        .toList();
            } catch (IOException error) {
                log.debug("列出技能资源 {} 失败：{}", root, error.getMessage());
                return List.of();
            }
        }
    }
}