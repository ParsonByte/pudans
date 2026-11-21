package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class L2Pipeline {

    private static final int MAX_L1_BATCH = 500;

    private final MemoryPromptResolver promptResolver;

    private final AiMemoryL1Repository l1Repository;

    private final AiMemoryL2Repository l2Repository;

    private final AiConversationRepository conversationRepository;

    private final AiMemoryGenerationLogRepository generationLogRepository;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Mono<Result> process(Request request, SceneExtractor extractor) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(extractor, "extractor");
        var promptContext = MemoryPromptContext.compose(promptResolver, AiMemoryPrompt.Layer.L2, request.prompt());
        return l1Repository
                .findActiveByScope(request.teamId(), request.agentId(), request.afterL1Id(), request.limit())
                .collectList()
                .flatMap(newL1 -> {
                    if (newL1.isEmpty()) {
                        return Mono.just(new Result(List.of(), request.afterL1Id(), 0, 0));
                    }
                    var lastL1Id = newL1.get(newL1.size() - 1).id();
                    return l2Repository
                            .findByScope(request.teamId(), request.agentId())
                            .collectList()
                            .flatMap(existing -> applyExtraction(
                                    request, newL1, existing, extractor, promptContext))
                            .flatMap(blocks -> conversationRepository
                                    .advanceL2Cursor(request.conversationId(), lastL1Id)
                                    .map(advanced -> lastL1Id)
                                    .map(cursor -> new Result(blocks, cursor, newL1.size(), blocks.size())));
                });
    }

    private Mono<List<L2SceneBlock>> applyExtraction(
            Request request,
            List<L1MemoryRecord> newL1,
            List<L2SceneBlock> existing,
            SceneExtractor extractor,
            MemoryPromptContext promptContext) {
        var extracted = extractor.extract(promptContext.systemPrompt(), List.copyOf(newL1), List.copyOf(existing));
        if (extracted == null || extracted.isEmpty()) {
            return Mono.just(List.of());
        }
        var now = clock.instant();
        var blocks = mergeExtracted(request, newL1, extracted, existing, now);
        return Flux.fromIterable(blocks)
                .concatMap(block -> l2Repository
                        .upsert(block)
                        .flatMap(inserted -> generationLogRepository
                                .insert(promptContext.toGenerationLog(
                                        inserted.id(),
                                        request.teamId(),
                                        request.agentId(),
                                        null,
                                        null,
                                        request.generation()))
                                .thenReturn(inserted)))
                .collectList();
    }

    private static List<L2SceneBlock> mergeExtracted(
            Request request,
            List<L1MemoryRecord> newL1,
            List<ExtractedScene> extracted,
            List<L2SceneBlock> existing,
            Instant now) {
        var knownL1Ids = new HashSet<Long>();
        for (var record : newL1) {
            knownL1Ids.add(record.id());
        }
        for (var block : existing) {
            knownL1Ids.addAll(block.l1Ids());
        }
        var existingByKey = new LinkedHashMap<String, L2SceneBlock>();
        for (var block : existing) {
            existingByKey.putIfAbsent(block.sceneKey(), block);
        }
        var merged = new LinkedHashMap<String, L2SceneBlock>();
        for (var scene : extracted) {
            for (var l1Id : scene.l1Ids()) {
                if (!knownL1Ids.contains(l1Id)) {
                    throw new IllegalArgumentException("场景引用了未知的 L1 记忆: " + l1Id);
                }
            }
            var incoming = new L2SceneBlock(
                    null,
                    request.teamId(),
                    request.agentId(),
                    scene.sceneKey(),
                    scene.title(),
                    scene.summary(),
                    scene.l1Ids(),
                    0,
                    scene.confidence(),
                    now);
            var current = merged.get(scene.sceneKey());
            var base = current != null ? current : existingByKey.get(scene.sceneKey());
            merged.put(scene.sceneKey(), base == null ? incoming : base.merge(incoming, now));
        }
        validateDistinctL1Ids(merged.values());
        return List.copyOf(merged.values());
    }

    private static void validateDistinctL1Ids(Iterable<L2SceneBlock> blocks) {
        var owners = new HashMap<Long, String>();
        for (var block : blocks) {
            for (var l1Id : block.l1Ids()) {
                var previous = owners.putIfAbsent(l1Id, block.sceneKey());
                if (previous != null && !previous.equals(block.sceneKey())) {
                    throw new IllegalArgumentException("L1 记忆不能同时属于多个场景: " + l1Id);
                }
            }
        }
    }

    @FunctionalInterface
    public interface SceneExtractor {
        List<ExtractedScene> extract(
                String systemPrompt, List<L1MemoryRecord> newL1, List<L2SceneBlock> existingScenes);
    }

    public record ExtractedScene(
            String sceneKey, String title, String summary, List<Long> l1Ids, BigDecimal confidence) {

        public ExtractedScene {
            requireText(sceneKey, "sceneKey");
            requireText(title, "title");
            requireText(summary, "summary");
            l1Ids = l1Ids == null ? List.of() : List.copyOf(l1Ids);
            if (l1Ids.isEmpty()) {
                throw new IllegalArgumentException("l1Ids 不能为空");
            }
            Objects.requireNonNull(confidence, "confidence");
            if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("confidence 必须在 0 到 1 之间");
            }
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record Request(
            String teamId,
            String agentId,
            long conversationId,
            long afterL1Id,
            int limit,
            MemoryPromptInput prompt,
            MemoryGenerationInput generation) {

        public Request {
            requireText(teamId, "teamId");
            requireText(agentId, "agentId");
            if (conversationId <= 0) {
                throw new IllegalArgumentException("conversationId 必须大于 0");
            }
            if (afterL1Id < 0) {
                throw new IllegalArgumentException("afterL1Id 不能为负数");
            }
            if (limit < 1 || limit > MAX_L1_BATCH) {
                throw new IllegalArgumentException("limit 必须在 1 到 " + MAX_L1_BATCH + " 之间");
            }
            prompt = prompt == null ? MemoryPromptInput.none() : prompt;
            generation = generation == null ? MemoryGenerationInput.empty() : generation;
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record Result(List<L2SceneBlock> blocks, long l2Cursor, int l1Processed, int generationLogs) {

        public Result {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }
}
