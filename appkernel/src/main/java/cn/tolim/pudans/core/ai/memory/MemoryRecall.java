package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.prompt.MemoryPrompts;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryRecallRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryRecallRepository.RecalledMemory;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiOrgMemoryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MemoryRecall {

    private static final Logger log = LoggerFactory.getLogger(MemoryRecall.class);

    private static final String PREPEND_BUDGET_KEY = "${pudans.ai.memory.recall.prepend-budget:3000}";

    private static final String APPEND_BUDGET_KEY = "${pudans.ai.memory.recall.append-budget:4000}";

    private final AiMemoryRecallRepository recallRepository;

    private final AiMemoryL3Repository personaRepository;

    private final MemoryPromptResolver promptResolver;

    private final @Nullable Embeddings embeddingService;

    private final @Nullable AiOrgMemoryRepository orgMemoryRepository;

    private final int prependBudget;

    private final int appendBudget;

    public MemoryRecall(
            AiMemoryRecallRepository recallRepository,
            AiMemoryL3Repository personaRepository,
            MemoryPromptResolver promptResolver,
            int prependBudget,
            int appendBudget) {
        this(recallRepository, personaRepository, promptResolver, null, null, prependBudget, appendBudget);
    }

    @Autowired
    public MemoryRecall(
            AiMemoryRecallRepository recallRepository,
            AiMemoryL3Repository personaRepository,
            MemoryPromptResolver promptResolver,
            @Nullable Embeddings embeddingService,
            @Nullable AiOrgMemoryRepository orgMemoryRepository,
            @Value(PREPEND_BUDGET_KEY) int prependBudget,
            @Value(APPEND_BUDGET_KEY) int appendBudget) {
        this.recallRepository = Objects.requireNonNull(recallRepository, "recallRepository");
        this.personaRepository = Objects.requireNonNull(personaRepository, "personaRepository");
        this.promptResolver = Objects.requireNonNull(promptResolver, "promptResolver");
        this.embeddingService = embeddingService;
        this.orgMemoryRepository = orgMemoryRepository;
        if (prependBudget < 1 || appendBudget < 1) {
            throw new IllegalArgumentException("字符预算必须大于 0");
        }
        this.prependBudget = prependBudget;
        this.appendBudget = appendBudget;
    }

    public Mono<MemoryInjection> build(MemoryContext context, String query, int l1Limit, int l2Limit) {
        return build(context, query, l1Limit, l2Limit, null, List.of());
    }

    public Mono<MemoryInjection> build(
            MemoryContext context,
            String query,
            int l1Limit,
            int l2Limit,
            @Nullable ResolvedMemoryPrompt customPrompt) {
        return build(context, query, l1Limit, l2Limit, customPrompt, List.of());
    }

    public Mono<MemoryInjection> build(
            MemoryContext context,
            String query,
            int l1Limit,
            int l2Limit,
            @Nullable ResolvedMemoryPrompt customPrompt,
            List<MemoryContext> borrowedScopes) {
        Objects.requireNonNull(context, "context");
        var factLimit = Math.max(l1Limit, 0);
        var sceneLimit = Math.max(l2Limit, 0);
        var normalizedQuery = query == null ? "" : query.strip();
        var scopes = mergeScopes(context, borrowedScopes);
        var facts = factLimit == 0 || normalizedQuery.isEmpty()
                ? Flux.<RecalledMemory>empty()
                : recallFacts(context, scopes, normalizedQuery, factLimit).flatMapMany(Flux::fromIterable);
        var scenes = sceneLimit == 0
                ? Flux.<ScopedScene>empty()
                : Flux.fromIterable(scopes)
                        .concatMap(scope -> recallRepository
                                .findScenes(scope.teamId(), scope.agentId(), sceneLimit)
                                .map(scene -> new ScopedScene(scope, scene)))
                        .collectList()
                        .map(MemoryRecall::dedupeScenes)
                        .flatMapMany(Flux::fromIterable);
        var personas = Flux.fromIterable(scopes)
                .concatMap(scope -> personaRepository
                        .findByScope(scope.teamId(), scope.agentId())
                        .map(persona -> new ScopedPersona(scope, persona)))
                .collectList()
                .map(MemoryRecall::dedupePersonas);
        return Mono.zip(facts.collectList(), scenes.collectList(), personas)
                .map(result -> compose(result.getT1(), result.getT2(), result.getT3(), customPrompt));
    }

    private static List<MemoryContext> mergeScopes(
            MemoryContext origin, @Nullable List<MemoryContext> borrowedScopes) {
        var scopes = new ArrayList<MemoryContext>();
        scopes.add(origin);
        if (borrowedScopes != null) {
            borrowedScopes.stream().filter(Objects::nonNull).forEach(scopes::add);
        }
        return List.copyOf(scopes);
    }

    // 优先使用向量召回，嵌入不可用或没有命中时回退到关键词评分
    private Mono<List<RecalledMemory>> recallFacts(
            MemoryContext origin, List<MemoryContext> scopes, String query, int limit) {
        var keyword = Flux.fromIterable(scopes)
                .concatMap(scope -> recallRepository.searchL1(
                        scope.teamId(), scope.agentId(), scope.userId(), query, limit))
                .collectList();
        var vector = embeddingQuery(query)
                .flatMap(vectorValue -> Flux.fromIterable(scopes)
                        .concatMap(scope -> recallRepository.searchL1Vector(
                                scope.teamId(),
                                scope.agentId(),
                                scope.userId(),
                                vectorValue,
                                limit))
                        .collectList())
                .filter(facts -> !facts.isEmpty());
        return vector.switchIfEmpty(Mono.defer(() -> keyword))
                .flatMap(l1 -> orgFacts(origin, query, limit)
                        .map(org -> mergeFacts(l1, org, limit)));
    }

    private Mono<float[]> embeddingQuery(String query) {
        if (embeddingService == null || query.isEmpty()) {
            return Mono.empty();
        }
        return embeddingService
                .embedOne(query)
                .onErrorResume(error -> {
                    log.debug("记忆召回嵌入不可用，回退关键词召回：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<List<RecalledMemory>> orgFacts(MemoryContext origin, String query, int limit) {
        if (orgMemoryRepository == null || !hasText(origin.teamId())) {
            return Mono.just(List.of());
        }
        return orgMemoryRepository
                .searchVerified(origin.teamId(), query, Math.max(1, Math.min(limit, 5)))
                .map(MemoryRecall::fromOrg)
                .collectList()
                .onErrorResume(error -> {
                    log.debug("组织记忆召回失败：{}", error.getMessage());
                    return Mono.just(List.of());
                });
    }

    private static RecalledMemory fromOrg(OrgMemoryRecord record) {
        return new RecalledMemory(
                record.id(),
                "org:" + record.id(),
                record.content(),
                MemoryType.WORK_FACT,
                "组织记忆",
                80,
                1,
                record.updatedAt() == null ? Instant.now() : record.updatedAt(),
                0.8);
    }

    private static List<RecalledMemory> mergeFacts(
            List<RecalledMemory> local, List<RecalledMemory> org, int limit) {
        var merged = new LinkedHashMap<String, RecalledMemory>();
        for (var fact : local) {
            merged.putIfAbsent(factKey(fact), fact);
        }
        for (var fact : org) {
            merged.putIfAbsent(factKey(fact), fact);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RecalledMemory::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private static String factKey(RecalledMemory fact) {
        return hasText(fact.recordId()) ? "record:" + fact.recordId() : "id:" + fact.id();
    }

    private static List<ScopedScene> dedupeScenes(List<ScopedScene> scenes) {
        var unique = new LinkedHashMap<String, ScopedScene>();
        for (var scene : scenes) {
            var key = agentScopeKey(scene.scope()) + "\n" + scene.scene().sceneKey();
            unique.putIfAbsent(key, scene);
        }
        return List.copyOf(unique.values());
    }

    private static List<ScopedPersona> dedupePersonas(List<ScopedPersona> personas) {
        var unique = new LinkedHashMap<String, ScopedPersona>();
        for (var persona : personas) {
            unique.putIfAbsent(agentScopeKey(persona.scope()), persona);
        }
        return List.copyOf(unique.values());
    }

    private static String agentScopeKey(MemoryContext scope) {
        return scope.teamId() + "\n" + scope.agentId();
    }

    private static String scopeKey(MemoryContext scope) {
        return scope.teamId() + "\n" + scope.userId() + "\n" + scope.agentId();
    }

    private MemoryInjection compose(
            List<RecalledMemory> facts,
            List<ScopedScene> scenes,
            List<ScopedPersona> personas,
            @Nullable ResolvedMemoryPrompt customPrompt) {
        var hasPersona = personas.stream().anyMatch(persona -> hasText(persona.persona().personaText()));
        var hasStrategy = customPrompt != null && customPrompt.isCustomStrategy();
        if (facts.isEmpty() && !hasPersona && scenes.isEmpty() && !hasStrategy) {
            return MemoryInjection.empty();
        }
        var refs = facts.stream().map(MemoryRecall::toRef).toList();
        return new MemoryInjection(
                truncate(renderFacts(facts), prependBudget),
                truncate(renderSystemContext(personas, scenes, customPrompt), appendBudget),
                refs);
    }

    private static MemoryInjection.Ref toRef(RecalledMemory fact) {
        return new MemoryInjection.Ref(
                fact.id(), fact.recordId(), fact.content(), fact.type(), fact.sceneName(), fact.score());
    }

    private static String renderFacts(List<RecalledMemory> facts) {
        if (facts.isEmpty()) {
            return "";
        }
        var builder = new StringBuilder();
        builder.append(MemoryPrompts.RECALL_HEADER)
                .append(MemoryPrompts.REFERENCE_NOTICE)
                .append('\n');
        var index = 1;
        for (var fact : facts) {
            builder.append(index++)
                    .append(". [")
                    .append(fact.type().name())
                    .append("][场景: ")
                    .append(hasText(fact.sceneName()) ? fact.sceneName() : "未分类")
                    .append("][相关度: ")
                    .append(String.format(Locale.ROOT, "%.2f", fact.score()))
                    .append("] ")
                    .append(fact.content())
                    .append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private String renderSystemContext(
            List<ScopedPersona> personas,
            List<ScopedScene> scenes,
            @Nullable ResolvedMemoryPrompt customPrompt) {
        var builder = new StringBuilder();
        builder.append(MemoryPrompts.STABLE_HEADER).append(MemoryPrompts.REFERENCE_NOTICE);
        var describedPersonas =
                personas.stream().filter(persona -> hasText(persona.persona().personaText())).toList();
        if (describedPersonas.size() == 1) {
            builder.append(MemoryPrompts.PERSONA_HEADER)
                    .append(describedPersonas.get(0).persona().personaText().strip());
        } else if (describedPersonas.size() > 1) {
            for (var persona : describedPersonas) {
                builder.append(MemoryPrompts.PERSONA_HEADER_PREFIX)
                        .append(sourceLabel(persona.scope()))
                        .append("】\n")
                        .append(persona.persona().personaText().strip());
            }
        }
        if (!scenes.isEmpty()) {
            var groups = new LinkedHashMap<String, List<ScopedScene>>();
            for (var scene : scenes) {
                groups.computeIfAbsent(scopeKey(scene.scope()), key -> new ArrayList<>()).add(scene);
            }
            for (var group : groups.values()) {
                if (groups.size() == 1) {
                    builder.append(MemoryPrompts.SCENE_HEADER);
                } else {
                    builder.append(MemoryPrompts.SCENE_HEADER_PREFIX)
                            .append(sourceLabel(group.get(0).scope()))
                            .append("】");
                }
                for (var scene : group) {
                    builder.append("\n- [")
                            .append(scene.scene().sceneKey())
                            .append("] ")
                            .append(scene.scene().title())
                            .append(": ")
                            .append(scene.scene().summary())
                            .append("（热度: ")
                            .append(scene.scene().heat())
                            .append("）");
                }
            }
        }
        builder.append(MemoryPrompts.TOOL_HEADER).append(MemoryPrompts.TOOL_GUIDANCE);
        if (customPrompt != null && customPrompt.isCustomStrategy()) {
            builder.append(MemoryPrompts.STRATEGY_HEADER)
                    .append(promptResolver.appendStrategy("", customPrompt).strip());
        }
        return builder.toString();
    }

    private static String sourceLabel(MemoryContext scope) {
        return scope.teamId() + " / " + scope.userId() + " / " + scope.agentId();
    }

    private record ScopedScene(MemoryContext scope, L2SceneBlock scene) {}

    private record ScopedPersona(MemoryContext scope, L3Persona persona) {}

    private static String truncate(String value, int budget) {
        if (value.length() <= budget) {
            return value;
        }
        var cut = budget;
        // 回退一个字符，避免把 UTF-16 代理对拆成孤立的高位
        if (Character.isHighSurrogate(value.charAt(cut - 1))) {
            cut--;
        }
        return value.substring(0, cut);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
