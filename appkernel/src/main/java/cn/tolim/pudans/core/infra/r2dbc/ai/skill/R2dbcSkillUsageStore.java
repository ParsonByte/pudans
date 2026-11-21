package cn.tolim.pudans.core.infra.r2dbc.ai.skill;

import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageRecord;
import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageStore;
import cn.tolim.pudans.core.infra.r2dbc.ai.skill.AiSkillUsageRepository.NamedUsage;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 同步接口读取本地缓存，写入异步落库并定期刷新，避免阻塞 Agent 执行线程
@Slf4j
@Component
@RequiredArgsConstructor
public class R2dbcSkillUsageStore implements SkillUsageStore, SmartLifecycle {

    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final AiSkillUsageRepository repository;

    private final ConcurrentMap<String, SkillUsageRecord> records = new ConcurrentHashMap<>();

    private volatile boolean running;

    private Disposable refresh;

    @Override
    public Optional<SkillUsageRecord> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(name));
    }

    @Override
    public List<SkillUsageRecord> all() {
        return records.values().stream()
                .sorted(Comparator.comparing(SkillUsageRecord::lastActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(record -> String.valueOf(record.createdBy())))
                .toList();
    }

    @Override
    public void recordView(String name) {
        update(name, record -> record.view(java.time.Instant.now()), repository::recordView, "view");
    }

    @Override
    public void recordUse(String name) {
        update(name, record -> record.use(java.time.Instant.now()), repository::recordUse, "use");
    }

    @Override
    public void recordPatch(String name) {
        update(name, record -> record.patch(java.time.Instant.now()), repository::recordPatch, "patch");
    }

    @Override
    public void markPromoted(String name, String reviewerId, List<String> environments) {
        update(
                name,
                record -> record.promoted(reviewerId, environments, java.time.Instant.now()),
                key -> repository.markPromoted(key, reviewerId, environments),
                "promote");
    }

    @Override
    public void setState(String name, SkillUsageRecord.State state) {
        if (state == null) {
            throw new IllegalArgumentException("技能状态不能为空。");
        }
        update(name, record -> record.withState(state), key -> repository.setState(key, state.name()), "state");
    }

    private void update(
            String name,
            java.util.function.UnaryOperator<SkillUsageRecord> change,
            java.util.function.Function<String, Mono<NamedUsage>> persist,
            String operation) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("技能名称不能为空。");
        }
        records.compute(name, (key, current) -> change.apply(current == null ? SkillUsageRecord.defaults() : current));
        persist.apply(name).subscribe(
                updated -> records.put(name, updated.record()),
                error -> log.debug("技能 {} 的 {} 记录落库失败：{}", name, operation, error.getMessage()));
    }

    private void refresh() {
        repository.list().subscribe(
                row -> records.put(row.name(), row.record()),
                error -> log.debug("刷新技能使用统计失败：{}", error.getMessage()));
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        refresh();
        refresh = Flux.interval(REFRESH_INTERVAL)
                .concatMap(tick -> repository.list()
                        .doOnNext(row -> records.put(row.name(), row.record()))
                        .then())
                .onErrorResume(error -> {
                    log.debug("刷新技能使用统计失败：{}", error.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.warn("技能使用统计刷新调度异常退出。", error));
    }

    @Override
    public void stop() {
        running = false;
        if (refresh != null) {
            refresh.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}