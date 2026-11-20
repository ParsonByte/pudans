package cn.tolim.pudans.core.ai.harness.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

final class SkillFiles {

    static final String SKILL_FILE = "SKILL.md";

    private SkillFiles() {}

    static boolean isVisible(String name) {
        return !name.isEmpty() && !name.startsWith(".") && !name.startsWith("_");
    }

    static @Nullable String safeRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        var value = relativePath.replace('\\', '/').strip();
        if (value.startsWith("/")) {
            return null;
        }
        try {
            var path = Path.of(value).normalize();
            if (path.isAbsolute() || path.startsWith("..") || path.toString().isEmpty()) {
                return null;
            }
            return path.toString().replace('\\', '/');
        } catch (RuntimeException error) {
            return null;
        }
    }

    static Path resolve(Path root, String relativePath) {
        var safe = safeRelative(relativePath);
        if (safe == null) {
            throw new IllegalArgumentException("技能资源路径不合法：" + relativePath);
        }
        var resolved = root.resolve(safe).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("技能资源路径不能越界：" + relativePath);
        }
        if (Files.exists(root)) {
            var probe = resolved;
            while (probe != null && !Files.exists(probe)) {
                probe = probe.getParent();
            }
            if (probe == null) {
                throw new IllegalArgumentException("技能资源路径不能越界：" + relativePath);
            }
            try {
                if (!probe.toRealPath().startsWith(root.toRealPath())) {
                    throw new IllegalArgumentException("技能资源路径不能越界：" + relativePath);
                }
            } catch (IOException error) {
                throw new UncheckedIOException("无法解析技能资源路径：" + probe, error);
            }
        }
        return resolved;
    }

    static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("技能名称不能为空。");
        }
        if (name.contains("/")
                || name.contains("\\")
                || ".".equals(name)
                || "..".equals(name)
                || name.contains("\0")) {
            throw new IllegalArgumentException("技能名称不合法：" + name);
        }
        return name;
    }

    static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    static String sourceOrDefault(@Nullable String source, String fallback) {
        return source == null || source.isBlank() ? fallback : source;
    }
}