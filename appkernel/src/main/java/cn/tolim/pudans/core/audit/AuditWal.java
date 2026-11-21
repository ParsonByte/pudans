package cn.tolim.pudans.core.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.PudansTime;

@Slf4j
@Component
public class AuditWal {

    private static final String DIRECTORY = "audit-wal";

    private static final String SUFFIX = ".jsonl";

    private final Object writeLock = new Object();

    private final Path directory;

    public AuditWal(PudansProperties properties) {
        this.directory = properties.getWorkDir() == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "pudans-audit-wal")
                : properties.getWorkDir().resolve(DIRECTORY);
    }

    public void append(AuditRecord record) {
        var serialized = serialize(record);
        synchronized (writeLock) {
            try {
                Files.createDirectories(directory);
                var file = directory.resolve("audit-" + LocalDate.now(PudansTime.BEIJING) + SUFFIX);
                Files.writeString(
                        file,
                        serialized + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException e) {
                log.error("无法将审计记录持久化到本地 WAL，记录内容：{}", serialized, e);
            }
        }
    }

    public void replay(Consumer<AuditRecord> consumer) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .forEach(file -> replayFile(file, consumer));
        } catch (IOException e) {
            log.error("无法列出审计 WAL 目录 [{}]", directory, e);
        }
    }

    private void replayFile(Path file, Consumer<AuditRecord> consumer) {
        var records = new ArrayList<AuditRecord>();
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank()).forEach(line -> {
                try {
                    records.add(JsonUtils.jsonToObject(line, AuditRecord.class));
                } catch (RuntimeException e) {
                    log.error("丢弃不可读的审计 WAL 条目", e);
                }
            });
        } catch (IOException e) {
            log.error("无法读取审计 WAL 文件 [{}]", file, e);
            return;
        }
        records.forEach(consumer);
        try {
            Files.move(
                    file,
                    file.resolveSibling(file.getFileName() + ".replayed"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("无法归档已重放的审计 WAL 文件 [{}]", file, e);
        }
        if (!records.isEmpty()) {
            log.warn("已重放 {} 条审计记录，来源本地 WAL 文件 [{}]", records.size(), file);
        }
    }

    private static String serialize(AuditRecord record) {
        try {
            return JsonUtils.objectToJson(record);
        } catch (RuntimeException e) {
            return "{\"unserializable\":\""
                    + record.getClass().getName().replace("\"", "")
                    + "\",\"name\":\""
                    + record.getMetadata().getName()
                    + "\"}";
        }
    }
}
