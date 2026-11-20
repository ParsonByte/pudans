package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.core.ai.prompt.MemoryPrompts;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryRecallRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryRecallRepository.RecalledMemory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AiMemoryRecallService {

    private static final String PREPEND_BUDGET_KEY = "${pudans.ai.memory.recall.prepend-budget:3000}";

    private static final String APPEND_BUDGET_KEY = "${pudans.ai.memory.recall.append-budget:4000}";

    private final AiMemoryRecallRepository recallRepository;

    private final AiMemoryL3Repository personaRepository;

    private final MemoryPromptResolver promptResolver;

    private final int prependBudget;

    private final int appendBudget;

    public AiMemoryRecallService(
            AiMemoryRecallRepository recallRepository,
            AiMemoryL3Repository personaRepository,
            MemoryPromptResolver promptResolver,
            @Value(PREPEND_BUDGET_KEY) int prependBudget,
            @Value(APPEND_BUDGET_KEY) int appendBudget) {
        this.recallRepository = Objects.requireNonNull(recallRepository, "recallRepository");
        this.personaRepository = Objects.requireNonNull(personaRepository, "personaRepository");
        this.promptResolver = Objects.requireNonNull(promptResolver, "promptResolver");
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
                : Flux.fromIterable(scopes)
                        .concatMap(scope -> recallRepository.searchL1(
                                scope.teamId(), scope.agentId(), scope.userId(), normalizedQuery, factLimit))
                        .collectList()
                        .map(AiMemoryRecallService::dedupeFacts)
                        .flatMapMany(Flux::fromIterable);
        var scenes = sceneLimit == 0
                ? Flux.<ScopedScene>empty()
                : Flux.fromIterable(scopes)
                        .concatMap(scope -> recallRepository
                                .findScenes(scope.teamId(), scope.agentId(), sceneLimit)
                                .map(scene -> new ScopedScene(scope, scene)))
                        .collectList()
                        .map(AiMemoryRecallService::dedupeScenes)
                        .flatMapMany(Flux::fromIterable);
        var personas = Flux.fromIterable(scopes)
                .concatMap(scope -> personaRepository
                        .findByScope(scope.teamId(), scope.agentId())
                        .map(persona -> new ScopedPersona(scope, persona)))
                .collectList()
                .map(AiMemoryRecallService::dedupePersonas);
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

    private static List<RecalledMemory> dedupeFacts(List<RecalledMemory> facts) {
        var unique = new LinkedHashMap<String, RecalledMemory>();
        for (var fact : facts) {
            var key = hasText(fact.recordId()) ? "record:" + fact.recordId() : "id:" + fact.id();
            unique.putIfAbsent(key, fact);
        }
        return List.copyOf(unique.values());
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
        var refs = facts.stream().map(AiMemoryRecallService::toRef).toList();
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
