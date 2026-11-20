package cn.tolim.pudans.core.ai.harness.tool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Mono;

public final class InMemoryToolResultStore implements ToolResultStore {

    private final ConcurrentMap<String, String> results = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Mono<String> save(String runId, String toolName, String output) {
        return Mono.fromSupplier(() -> {
            var id = "tool-result-" + sequence.incrementAndGet();
            results.put(id, output);
            return id;
        });
    }

    @Override
    public Mono<String> read(String id) {
        return Mono.justOrEmpty(results.get(id));
    }
}