package cn.tolim.pudans.infra.r2dbc.ai.skill;

import cn.tolim.pudans.core.ai.harness.skill.curator.SkillAuditLog;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

// 审计写入异步落库，本地保留最近记录供接口即时读取
@Slf4j
@Component
@RequiredArgsConstructor
public class R2dbcSkillAuditLog implements SkillAuditLog, SmartLifecycle {

    private static final int CACHE_LIMIT = 500;

    private static final int LOAD_LIMIT = 200;

    private final AiSkillAuditRepository repository;

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    @Override
    public void append(Entry entry) {
        Objects.requireNonNull(entry, "审计记录不能为空。");
        entries.add(entry);
        while (entries.size() > CACHE_LIMIT) {
            entries.removeFirst();
        }
        repository.insert(entry).subscribe(
                ignored -> {},
                error -> log.debug("技能审计 {} 落库失败：{}", entry.target(), error.getMessage()));
    }

    @Override
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    private void refresh() {
        repository.recent(LOAD_LIMIT).subscribe(
                loaded -> {
                    if (entries.size() < LOAD_LIMIT) {
                        entries.add(loaded);
                    }
                },
                error -> log.debug("加载技能审计日志失败：{}", error.getMessage()));
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        refresh();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}