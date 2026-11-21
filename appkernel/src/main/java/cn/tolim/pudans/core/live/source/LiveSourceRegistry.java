package cn.tolim.pudans.core.live.source;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 已启用直播源的内存快照，监控 tick 从这里选取到期源并统计状态
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSourceRegistry {

    private final ReactiveExtensionClient client;

    private final LiveMetrics metrics;

    private final ConcurrentHashMap<String, Snapshot> sources = new ConcurrentHashMap<>();

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public record Snapshot(
            String name,
            LivePlatform platform,
            String roomId,
            LiveSourceState state,
            boolean live,
            boolean rateLimited,
            boolean autoRecord,
            int checkIntervalSeconds,
            Instant nextCheckAt,
            Instant cooldownUntil) {}

    public record Counts(
            long enabled, long live, long recording, long offline, long error, long rateLimited) {}

    @EventListener(ApplicationReadyEvent.class)
    public void loadEnabled() {
        var options = ListOptions.builder().andQuery(equal("spec.enabled", true)).build();
        client.listAll(LiveSource.class, options, defaultSort())
                .filter(source -> !isDeleted(source) && source.getSpec().isEnabled())
                .doOnNext(this::upsert)
                .doOnError(error -> log.warn("加载已启用直播源失败：{}", error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    public void upsert(LiveSource source) {
        if (source == null || source.getMetadata() == null
                || !StringUtils.hasText(source.getMetadata().getName())) {
            return;
        }
        var name = source.getMetadata().getName();
        var spec = source.getSpec();
        if (isDeleted(source) || !spec.isEnabled()) {
            remove(name);
            return;
        }
        var status = source.getStatus();
        sources.put(name, new Snapshot(
                name,
                spec.getPlatform(),
                spec.getRoomId(),
                status.getState(),
                status.isLive(),
                status.isRateLimited(),
                spec.isAutoRecord(),
                spec.getCheckIntervalSeconds(),
                status.getNextCheckAt(),
                status.getCooldownUntil()));
        metrics.sourcesActive(sources.size());
    }

    public void remove(String name) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        sources.remove(name);
        inFlight.remove(name);
        metrics.sourcesActive(sources.size());
    }

    // 选取到期且未在途的源，返回前先标记在途，避免同一源在一次 tick 中重复解析
    public synchronized List<Snapshot> snapshotDue(Instant now, int max) {
        var limit = Math.max(1, max);
        var ordered = sources.values().stream()
                .filter(snapshot -> snapshot.nextCheckAt() == null || !snapshot.nextCheckAt().isAfter(now))
                .filter(snapshot -> snapshot.cooldownUntil() == null || !snapshot.cooldownUntil().isAfter(now))
                .filter(snapshot -> !snapshot.rateLimited()
                        || snapshot.cooldownUntil() == null
                        || !snapshot.cooldownUntil().isAfter(now))
                .sorted(Comparator.comparing(Snapshot::nextCheckAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        var due = new ArrayList<Snapshot>();
        for (var snapshot : ordered) {
            if (due.size() >= limit) {
                break;
            }
            if (inFlight.add(snapshot.name())) {
                due.add(snapshot);
            }
        }
        return due;
    }

    public void release(String name) {
        if (StringUtils.hasText(name)) {
            inFlight.remove(name);
        }
    }

    public int size() {
        return sources.size();
    }

    public Counts counts() {
        long enabled = 0;
        long live = 0;
        long recording = 0;
        long offline = 0;
        long error = 0;
        long rateLimited = 0;
        for (var snapshot : sources.values()) {
            enabled++;
            if (snapshot.rateLimited()) {
                rateLimited++;
            }
            var state = snapshot.state();
            if (state == null) {
                continue;
            }
            switch (state) {
                case LIVE -> live++;
                case RECORDING -> {
                    live++;
                    recording++;
                }
                case OFFLINE -> offline++;
                case ERROR -> error++;
                default -> {
                }
            }
        }
        return new Counts(enabled, live, recording, offline, error, rateLimited);
    }
}
