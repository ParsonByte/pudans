package cn.tolim.pudans.core.ai.harness.workspace;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.util.StringUtils;

public record WorkspaceSpec(
        Path root, String skillsDirectory, String sandboxRoot, Map<String, String> environment) {

    public static final String DEFAULT_SKILLS_DIRECTORY = "skills";

    public static final String DEFAULT_SANDBOX_ROOT = "/workspace";

    public WorkspaceSpec {
        if (root == null) {
            throw new IllegalArgumentException("工作区根目录不能为空。");
        }
        root = root.toAbsolutePath().normalize();
        skillsDirectory = normalizeDirectory(skillsDirectory);
        sandboxRoot = normalizeSandboxRoot(sandboxRoot);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public static WorkspaceSpec defaults(Path root) {
        return new WorkspaceSpec(root, DEFAULT_SKILLS_DIRECTORY, DEFAULT_SANDBOX_ROOT, Map.of());
    }

    private static String normalizeDirectory(String directory) {
        if (!StringUtils.hasText(directory)) {
            return DEFAULT_SKILLS_DIRECTORY;
        }
        var value = directory.replace('\\', '/').strip();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        var path = Path.of(value).normalize();
        if (path.toString().isEmpty() || path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("工作区技能目录必须是工作区内的相对路径。");
        }
        return path.toString().replace('\\', '/');
    }

    private static String normalizeSandboxRoot(String root) {
        if (!StringUtils.hasText(root)) {
            return DEFAULT_SANDBOX_ROOT;
        }
        var value = root.strip();
        if (!value.startsWith("/") || value.contains("\0")) {
            throw new IllegalArgumentException("沙箱工作目录必须是以 / 开头的绝对路径。");
        }
        if (value.contains("/../") || value.endsWith("/..")) {
            throw new IllegalArgumentException("沙箱工作目录不能包含上级路径。");
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}