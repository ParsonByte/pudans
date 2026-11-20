package cn.tolim.pudans.core.ai.harness.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClasspathSkillRepository implements SkillRepository {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSkillRepository.class);

    private static final String DEFAULT_SOURCE = "classpath";

    private final Map<String, SkillEntry> entries;

    public ClasspathSkillRepository(String resourcePath) {
        this(resourcePath, ClasspathSkillRepository.class.getClassLoader(), DEFAULT_SOURCE);
    }

    public ClasspathSkillRepository(String resourcePath, ClassLoader classLoader) {
        this(resourcePath, classLoader, DEFAULT_SOURCE);
    }

    public ClasspathSkillRepository(String resourcePath, ClassLoader classLoader, String source) {
        if (classLoader == null) {
            throw new IllegalArgumentException("类加载器不能为空。");
        }
        var base = resourceRoot(resourcePath);
        var loaded = new LinkedHashMap<String, SkillEntry>();
        // 类路径资源位于 jar 内时无法可靠地增量列举，构造时一次载入
        try {
            var resources = classLoader.getResources(base);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    scanDirectory(Path.of(url.toURI()), source, loaded);
                } else if ("jar".equals(url.getProtocol())) {
                    scanJar(url, base, source, loaded);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException("无法读取类路径技能目录：" + base, error);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("类路径技能目录地址不合法：" + base, error);
        }
        this.entries = Collections.unmodifiableMap(loaded);
    }

    @Override
    public List<SkillEntry> list() {
        return List.copyOf(entries.values());
    }

    private static String resourceRoot(String resourcePath) {
        var safe = SkillFiles.safeRelative(resourcePath);
        if (safe == null) {
            throw new IllegalArgumentException("类路径技能目录不合法：" + resourcePath);
        }
        return safe;
    }

    private static void scanDirectory(
            Path baseDir, String source, Map<String, SkillEntry> loaded) throws IOException {
        if (!Files.isDirectory(baseDir)) {
            return;
        }
        List<Path> directories;
        try (var children = Files.list(baseDir)) {
            directories = children
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> SkillFiles.isVisible(path.getFileName().toString()))
                    .sorted()
                    .toList();
        }
        for (var directory : directories) {
            List<Path> files;
            try (var walked = Files.walk(directory)) {
                files = walked.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .sorted()
                        .toList();
            }
            var contents = new TreeMap<String, byte[]>();
            for (var file : files) {
                contents.put(SkillFiles.relative(directory, file), Files.readAllBytes(file));
            }
            addSkill(directory.getFileName().toString(), source, contents, loaded);
        }
    }

    private static void scanJar(
            URL url, String base, String source, Map<String, SkillEntry> loaded) throws IOException {
        var connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        var entryName = SkillFiles.safeRelative(connection.getEntryName());
        if (entryName == null) {
            return;
        }
        var prefix = entryName + "/";
        var grouped = new TreeMap<String, TreeMap<String, byte[]>>();
        try (var jar = connection.getJarFile()) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix)) {
                    continue;
                }
                var relative = name.substring(prefix.length());
                var slash = relative.indexOf('/');
                if (slash <= 0) {
                    continue;
                }
                var directoryName = relative.substring(0, slash);
                if (!SkillFiles.isVisible(directoryName)) {
                    continue;
                }
                var within = relative.substring(slash + 1);
                if (within.isEmpty()) {
                    continue;
                }
                try (var input = jar.getInputStream(entry)) {
                    grouped.computeIfAbsent(directoryName, key -> new TreeMap<>())
                            .put(within, input.readAllBytes());
                }
            }
        }
        for (var group : grouped.entrySet()) {
            addSkill(group.getKey(), source, group.getValue(), loaded);
        }
    }

    private static void addSkill(
            String directoryName,
            String source,
            Map<String, byte[]> contents,
            Map<String, SkillEntry> loaded) {
        var markdown = contents.remove(SkillFiles.SKILL_FILE);
        if (markdown == null) {
            return;
        }
        try {
            var entry = SkillEntry.fromMarkdown(
                    new String(markdown, StandardCharsets.UTF_8),
                    directoryName,
                    source,
                    new MapSkillResources(contents),
                    null);
            loaded.putIfAbsent(entry.name(), entry);
        } catch (RuntimeException error) {
            log.warn("解析类路径技能 {} 失败：{}", directoryName, error.getMessage());
        }
    }

    private static final class MapSkillResources implements SkillResources {

        private final Map<String, byte[]> contents;

        MapSkillResources(Map<String, byte[]> contents) {
            this.contents = Map.copyOf(contents);
        }

        @Override
        public Optional<String> read(String relativePath) {
            var safe = SkillFiles.safeRelative(relativePath);
            if (safe == null) {
                return Optional.empty();
            }
            var content = contents.get(safe);
            return content == null
                    ? Optional.empty()
                    : Optional.of(new String(content, StandardCharsets.UTF_8));
        }

        @Override
        public Optional<byte[]> readBinary(String relativePath) {
            var safe = SkillFiles.safeRelative(relativePath);
            if (safe == null) {
                return Optional.empty();
            }
            var content = contents.get(safe);
            return content == null ? Optional.empty() : Optional.of(content.clone());
        }

        @Override
        public List<String> list() {
            return contents.keySet().stream().sorted().toList();
        }
    }
}