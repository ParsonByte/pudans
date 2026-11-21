package cn.tolim.pudans.core.ai.memory.asset;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiMemoryAsset;
import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.conversation.Conversations;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.NewConversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryAssetRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository.LogEntry;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import cn.tolim.pudans.infra.r2dbc.ai.task.AiTaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

@Service
@RequiredArgsConstructor
public class MemoryAssets {

    public static final String CODE_NOT_FOUND = "ai-memory-asset-not-found";

    public static final String CODE_FORBIDDEN = "ai-memory-asset-forbidden";

    public static final String CODE_CONFLICT = "ai-memory-asset-conflict";

    public static final String CODE_INVALID = "ai-memory-asset-invalid";

    public static final String CODE_BINDING_LIMIT = "ai-memory-binding-limit";

    public static final String CODE_BINDING_INVALID = "ai-memory-binding-invalid";

    public static final String CODE_IMPORT_INVALID = "ai-memory-import-invalid";

    public static final String CODE_LOG_NOT_FOUND = "ai-memory-generation-log-not-found";

    private static final int DISPLAY_NAME_LIMIT = 200;

    private static final int MAX_AGENT_BINDINGS = 2;

    private static final int MAX_AGENTS_PER_ASSET = 2;

    private static final String AGENT_SOURCE_IMPORT = "pudans-memory-import";

    private static final String ROLE_USER = "user";

    private static final String ROLE_ASSISTANT = "assistant";

    private static final int MAX_IMPORT_MESSAGES = 100;

    private static final int MAX_SESSION_KEY_LENGTH = 256;

    private final ReactiveExtensionClient client;

    private final Teams teamService;

    private final AiMemoryAssetRepository assetRepository;

    private final AiMemoryGenerationLogRepository generationLogRepository;

    private final AiMemoryL1Repository l1Repository;

    private final AiMemoryL2Repository l2Repository;

    private final AiMemoryL3Repository l3Repository;

    private final Conversations conversations;

    private final MemoryTaskScheduler taskScheduler;

    public Mono<List<MemoryAssetView>> list(
            String userId,
            @Nullable String team,
            @Nullable String scope,
            @Nullable String agent,
            boolean mine,
            int limit) {
        return resolveTeams(userId, team).flatMap(teams -> descriptors(team).flatMap(all -> ownedAgents(userId, teams)
                .flatMap(owned -> {
                    var filtered = all.stream()
                            .filter(descriptor -> teams.contains(descriptor.spec().getTeam()))
                            .filter(descriptor -> canRead(userId, descriptor.spec()))
                            .filter(descriptor -> !mine || userId.equals(descriptor.spec().getOwnerUserId()))
                            .filter(descriptor -> agentMatches(descriptor, agent))
                            .filter(descriptor -> scopeMatches(descriptor, scope, owned))
                            .sorted(Comparator.comparing(Descriptor::name))
                            .limit(limit)
                            .toList();
                    return Flux.fromIterable(filtered)
                            .concatMap(this::view)
                            .collectList();
                })));
    }

    public Mono<MemoryAssetView> detail(String userId, String name, @Nullable String team) {
        return requireRead(userId, name, team).flatMap(this::view);
    }

    public Mono<MemoryLayerCounts> layerCounts(String userId, String name, @Nullable String team) {
        return requireRead(userId, name, team).flatMap(ref -> assetRepository.counts(
                ref.spec().getTeam(), ref.spec().getSourceUserId(), ref.spec().getSourceAgentId()));
    }

    public Mono<MemoryAssetView> create(String userId, CreateCommand command) {
        try {
            validateDisplayName(command.displayName());
            validateTeam(command.team());
            validateSourceAgent(command.sourceAgentId());
        } catch (ApiException error) {
            return Mono.error(error);
        }
        var sourceUserId =
                StringUtils.hasText(command.sourceUserId()) ? command.sourceUserId().strip() : userId;
        if (!sourceUserId.equals(userId)) {
            return Mono.error(forbidden("只能为当前用户的记忆作用域创建资产。"));
        }
        if (command.ownerUserId() != null && !command.ownerUserId().equals(userId)) {
            return Mono.error(forbidden("资产 owner 必须是当前用户。"));
        }
        var agents = normalizeAgents(command.agents(), command.sourceAgentId());
        if (agents.isError()) {
            return Mono.error(agents.error());
        }
        return requireTeam(userId, command.team())
                .then(requireAgent(command.sourceAgentId(), command.team()))
                .then(Mono.defer(() -> ensureUniqueAsset(
                        command.team(), sourceUserId, command.sourceAgentId(), null)))
                .then(validateAgentTargets(command.team(), sourceUserId, agents.value(), null))
                .then(Mono.defer(() -> {
                    var spec = new AiMemoryAsset.Spec();
                    spec.setDisplayName(command.displayName().strip());
                    spec.setDescription(command.description());
                    spec.setTeam(command.team());
                    spec.setOwnerUserId(userId);
                    spec.setSourceUserId(sourceUserId);
                    spec.setSourceAgentId(command.sourceAgentId());
                    spec.setVisibility(command.visibility() == null
                            ? AiMemoryAsset.Visibility.TEAM
                            : command.visibility());
                    spec.setStatus(command.status() == null ? AiMemoryAsset.Status.ACTIVE : command.status());
                    spec.setAutoGenerated(false);
                    spec.setAgents(agents.value());
                    spec.setCreatedByUserId(userId);
                    var asset = new AiMemoryAsset();
                    asset.setSpec(spec);
                    var metadata = new Metadata();
                    if (StringUtils.hasText(command.name())) {
                        metadata.setName(command.name().strip());
                    } else {
                        metadata.setGenerateName(StringUtils.hasText(command.generateName())
                                ? command.generateName().strip()
                                : MemoryAssetNames.autoName(
                                                command.team(), sourceUserId, command.sourceAgentId())
                                        + "-");
                    }
                    asset.setMetadata(metadata);
                    return client.create(asset)
                            .onErrorMap(DuplicateNameException.class, error -> conflict("资产名称已存在。"))
                            .onErrorMap(
                                    DataIntegrityViolationException.class,
                                    error -> conflict("资产名称已存在。"))
                            .flatMap(created -> view(new Descriptor(
                                    created.getMetadata().getName(), created.getSpec(), false)));
                }));
    }

    public Mono<MemoryAssetView> patch(
            String userId, String name, @Nullable String team, PatchCommand command) {
        return requireOwner(userId, name, team).flatMap(ref -> {
            var spec = copy(ref.asset().getSpec());
            if (command.displayName() != null) {
                validateDisplayName(command.displayName());
                spec.setDisplayName(command.displayName().strip());
            }
            if (command.description() != null) {
                spec.setDescription(command.description());
            }
            if (command.visibility() != null) {
                spec.setVisibility(command.visibility());
            }
            if (command.status() != null) {
                spec.setStatus(command.status());
            }
            var agents = command.agents() == null
                    ? null
                    : normalizeAgents(command.agents(), spec.getSourceAgentId());
            if (agents != null && agents.isError()) {
                return Mono.error(agents.error());
            }
            return Mono.justOrEmpty(agents == null ? null : agents.value())
                    .flatMapMany(Flux::fromIterable)
                    .filter(agent -> spec.getAgents() == null || !spec.getAgents().contains(agent))
                    .collectList()
                    .flatMap(added -> validateAgentTargets(
                            spec.getTeam(), spec.getSourceUserId(), added, ref.name()))
                    .then(Mono.defer(() -> {
                        if (agents != null) {
                            spec.setAgents(agents.value());
                        }
                        return persist(ref, spec);
                    }));
        });
    }

    public Mono<MemoryLayerCounts> delete(
            String userId, String name, @Nullable String team, boolean clear) {
        return requireOwner(userId, name, team).flatMap(ref -> {
            var cleared = clear
                    ? clearScope(ref.spec().getTeam(), ref.spec().getSourceUserId(), ref.spec().getSourceAgentId())
                    : Mono.just(MemoryLayerCounts.zero());
            return cleared.flatMap(counts -> {
                if (!ref.persisted()) {
                    return Mono.just(counts);
                }
                return client.delete(ref.asset()).thenReturn(counts);
            });
        });
    }

    public Mono<MemoryLayerCounts> clear(String userId, String name, @Nullable String team) {
        return requireOwner(userId, name, team)
                .flatMap(ref -> clearScope(
                        ref.spec().getTeam(), ref.spec().getSourceUserId(), ref.spec().getSourceAgentId()));
    }

    private Mono<MemoryLayerCounts> clearScope(String team, String sourceUserId, String sourceAgentId) {
        return Mono.zip(
                        assetRepository.deleteMessagesByScope(team, sourceUserId, sourceAgentId),
                        l1Repository.deleteByScope(team, sourceAgentId, sourceUserId),
                        l2Repository.deleteByScope(team, sourceAgentId),
                        l3Repository.deleteByScope(team, sourceAgentId))
                .map(result -> new MemoryLayerCounts(
                        result.getT1(), result.getT2(), result.getT3(), result.getT4()));
    }

    public Mono<MemoryAssetView> bind(String userId, String name, @Nullable String team, String agent) {
        if (!StringUtils.hasText(agent)) {
            return Mono.error(invalid("agent 不能为空。"));
        }
        return requireOwner(userId, name, team).flatMap(ref -> {
            var spec = ref.asset().getSpec();
            var target = agent.strip();
            if (target.equals(spec.getSourceAgentId())) {
                return Mono.error(bindingInvalid("资产不能绑定到自身的来源 Agent。"));
            }
            var current = spec.getAgents() == null ? List.<String>of() : spec.getAgents();
            if (current.contains(target)) {
                return Mono.error(bindingLimit("Agent 已绑定该资产。"));
            }
            if (current.size() >= MAX_AGENTS_PER_ASSET) {
                return Mono.error(bindingLimit("每个资产最多绑定两个 Agent。"));
            }
            return requireAgent(target, spec.getTeam())
                    .then(Mono.defer(() -> validateAgentTargets(
                            spec.getTeam(), spec.getSourceUserId(), List.of(target), ref.name())))
                    .then(Mono.defer(() -> {
                        var updated = new ArrayList<>(current);
                        updated.add(target);
                        var copy = copy(spec);
                        copy.setAgents(updated);
                        return persist(ref, copy);
                    }));
        });
    }

    public Mono<MemoryAssetView> unbind(String userId, String name, @Nullable String team, String agent) {
        return requireOwner(userId, name, team).flatMap(ref -> {
            var spec = ref.asset().getSpec();
            var current = spec.getAgents() == null ? List.<String>of() : spec.getAgents();
            if (!current.contains(agent)) {
                return view(ref);
            }
            var copy = copy(spec);
            copy.setAgents(current.stream().filter(bound -> !bound.equals(agent)).toList());
            return persist(ref, copy);
        });
    }

    public Mono<ImportResult> importMessages(
            String userId, String name, @Nullable String team, ImportCommand command) {
        var messages = validateImport(command.messages());
        if (messages.isError()) {
            return Mono.error(messages.error());
        }
        var sessionKey = command.sessionId();
        if (StringUtils.hasText(sessionKey) && sessionKey.length() > MAX_SESSION_KEY_LENGTH) {
            return Mono.error(importInvalid("sessionId 长度不能超过 256。"));
        }
        return requireOwner(userId, name, team).flatMap(ref -> ensurePersisted(ref)
                .flatMap(persisted -> {
                    var spec = persisted.asset().getSpec();
                    var conversation = new NewConversation(
                            AiTaskRepository.DEFAULT_SPACE,
                            spec.getSourceUserId(),
                            spec.getTeam(),
                            spec.getSourceAgentId(),
                            AGENT_SOURCE_IMPORT,
                            StringUtils.hasText(sessionKey)
                                    ? sessionKey
                                    : "memory-import-" + UUID.randomUUID(),
                            null,
                            spec.getDisplayName(),
                            null,
                            null);
                    return conversations
                            .createOrGet(conversation)
                            .flatMap(created -> appendImported(created, messages.value())
                                    .flatMap(last -> conversations
                                            .markPending(created, messages.value().size())
                                            .then(taskScheduler.enqueueExtractionForImport(
                                                    created, spec.getSourceUserId(), last.id()))
                                            .thenReturn(new ImportResult(
                                                    created.id(), messages.value().size()))));
                }));
    }

    public Flux<LogEntry> generationLogs(
            String userId,
            String name,
            @Nullable String team,
            AiMemoryPrompt.@Nullable Layer layer,
            @Nullable String status,
            @Nullable Instant from,
            @Nullable Instant to,
            long beforeId,
            int limit) {
        return requireRead(userId, name, team)
                .flatMapMany(ref -> generationLogRepository.listByScope(
                        ref.spec().getTeam(),
                        ref.spec().getSourceAgentId(),
                        layer,
                        status,
                        from,
                        to,
                        beforeId,
                        limit));
    }

    public Mono<LogEntry> generationLog(String userId, long id) {
        return generationLogRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, CODE_LOG_NOT_FOUND, "生成日志不存在。")))
                .flatMap(entry -> {
                    if (!StringUtils.hasText(entry.teamId())) {
                        return Mono.just(entry);
                    }
                    return teamService
                            .isMember(entry.teamId(), userId)
                            .flatMap(member -> Boolean.TRUE.equals(member)
                                    ? Mono.just(entry)
                                    : Mono.error(forbidden("无权访问该生成日志。")));
                });
    }

    Mono<AssetRef> requireRead(String userId, String name, @Nullable String team) {
        return resolve(team, name)
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(ref -> requireTeam(userId, ref.spec().getTeam()).then(Mono.defer(() -> {
                    if (canRead(userId, ref.spec())) {
                        return Mono.just(ref);
                    }
                    return Mono.error(forbidden("无权访问该记忆资产。"));
                })));
    }

    Mono<AssetRef> requireOwner(String userId, String name, @Nullable String team) {
        return resolve(team, name)
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(ref -> requireTeam(userId, ref.spec().getTeam()).then(Mono.defer(() -> {
                    if (userId.equals(ref.spec().getOwnerUserId())) {
                        return Mono.just(ref);
                    }
                    return Mono.error(forbidden("只有资产 owner 可以执行该操作。"));
                })));
    }

    Mono<AssetRef> resolve(@Nullable String team, String name) {
        return client.get(AiMemoryAsset.class, name)
                .map(asset -> new AssetRef(asset, true, name))
                .switchIfEmpty(Mono.defer(() -> descriptors(team)
                        .flatMapMany(Flux::fromIterable)
                        .filter(descriptor -> descriptor.auto() && descriptor.name().equals(name))
                        .next()
                        .map(descriptor -> new AssetRef(descriptor.asset(), false, descriptor.name()))));
    }

    Mono<MemoryAssetView> view(AssetRef ref) {
        return assetRepository
                .counts(ref.spec().getTeam(), ref.spec().getSourceUserId(), ref.spec().getSourceAgentId())
                .map(counts -> new MemoryAssetView(
                        ref.name(),
                        ref.spec().getDisplayName(),
                        ref.spec().getDescription(),
                        ref.spec().getTeam(),
                        ref.spec().getOwnerUserId(),
                        ref.spec().getSourceUserId(),
                        ref.spec().getSourceAgentId(),
                        ref.spec().getVisibility(),
                        ref.spec().getStatus(),
                        ref.spec().isAutoGenerated(),
                        ref.spec().getAgents(),
                        ref.spec().getCreatedByUserId(),
                        counts));
    }

    private Mono<MemoryAssetView> view(Descriptor descriptor) {
        return assetRepository
                .counts(
                        descriptor.spec().getTeam(),
                        descriptor.spec().getSourceUserId(),
                        descriptor.spec().getSourceAgentId())
                .map(counts -> new MemoryAssetView(
                        descriptor.name(),
                        descriptor.spec().getDisplayName(),
                        descriptor.spec().getDescription(),
                        descriptor.spec().getTeam(),
                        descriptor.spec().getOwnerUserId(),
                        descriptor.spec().getSourceUserId(),
                        descriptor.spec().getSourceAgentId(),
                        descriptor.spec().getVisibility(),
                        descriptor.spec().getStatus(),
                        descriptor.spec().isAutoGenerated(),
                        descriptor.spec().getAgents(),
                        descriptor.spec().getCreatedByUserId(),
                        counts));
    }

    private Mono<List<Descriptor>> descriptors(@Nullable String team) {
        return loadAssets().flatMap(assets -> {
            var matching = assets.stream()
                    .filter(asset -> team == null || team.equals(asset.getSpec().getTeam()))
                    .toList();
            var existing = new LinkedHashMap<String, Descriptor>();
            for (var asset : matching) {
                var spec = asset.getSpec();
                existing.put(scopeKey(spec.getTeam(), spec.getSourceUserId(), spec.getSourceAgentId()),
                        new Descriptor(asset.getMetadata().getName(), spec, false));
            }
            return autoDescriptors(team, existing.values().stream().toList()).map(auto -> {
                var all = new ArrayList<Descriptor>(existing.values());
                all.addAll(auto);
                return List.copyOf(all);
            });
        });
    }

    private Mono<List<Descriptor>> autoDescriptors(@Nullable String team, List<Descriptor> persisted) {
        var keys = new LinkedHashSet<String>();
        var names = new LinkedHashSet<String>();
        for (var descriptor : persisted) {
            var spec = descriptor.spec();
            keys.add(scopeKey(spec.getTeam(), spec.getSourceUserId(), spec.getSourceAgentId()));
            names.add(descriptor.name());
        }
        return Mono.zip(
                        assetRepository.findScopes(team).collectList(),
                        assetRepository.findAgentScopes(team).collectList(),
                        client.listAll(AiAgent.class, ListOptions.builder().build(), defaultSort())
                                .collectList())
                .map(tuple -> {
                    var descriptors = new LinkedHashMap<String, Descriptor>();
                    var agentScopes = new LinkedHashSet<String>();
                    for (var scope : tuple.getT1()) {
                        agentScopes.add(scope.teamId() + "\n" + scope.agentId());
                        addAuto(descriptors, names, keys, scope.teamId(), scope.userId(), scope.agentId());
                    }
                    var agents = tuple.getT3().stream()
                            .filter(agent -> agent.getSpec() != null && agent.getMetadata() != null)
                            .collect(Collectors.toMap(
                                    agent -> agent.getMetadata().getName(),
                                    agent -> agent,
                                    (first, second) -> first));
                    for (var scope : tuple.getT2()) {
                        if (agentScopes.contains(scope.teamId() + "\n" + scope.agentId())) {
                            continue;
                        }
                        var agent = agents.get(scope.agentId());
                        if (agent == null || !StringUtils.hasText(agent.getSpec().getOwnerUserId())) {
                            continue;
                        }
                        addAuto(
                                descriptors,
                                names,
                                keys,
                                scope.teamId(),
                                agent.getSpec().getOwnerUserId(),
                                scope.agentId());
                    }
                    return List.copyOf(descriptors.values());
                });
    }

    private static void addAuto(
            Map<String, Descriptor> descriptors,
            Set<String> names,
            Set<String> keys,
            String team,
            String sourceUserId,
            String sourceAgentId) {
        var key = scopeKey(team, sourceUserId, sourceAgentId);
        if (keys.contains(key) || !StringUtils.hasText(team) || !StringUtils.hasText(sourceUserId)) {
            return;
        }
        var name = MemoryAssetNames.autoName(team, sourceUserId, sourceAgentId);
        if (names.contains(name) || descriptors.containsKey(name)) {
            return;
        }
        var spec = new AiMemoryAsset.Spec();
        spec.setDisplayName(displayName(sourceUserId, sourceAgentId));
        spec.setTeam(team);
        spec.setOwnerUserId(sourceUserId);
        spec.setSourceUserId(sourceUserId);
        spec.setSourceAgentId(sourceAgentId);
        spec.setVisibility(AiMemoryAsset.Visibility.PRIVATE);
        spec.setStatus(AiMemoryAsset.Status.ACTIVE);
        spec.setAutoGenerated(true);
        spec.setCreatedByUserId(sourceUserId);
        descriptors.put(name, new Descriptor(name, spec, true));
    }

    private static String displayName(String sourceUserId, String sourceAgentId) {
        return sourceUserId + " / " + sourceAgentId;
    }

    private Mono<List<AiMemoryAsset>> loadAssets() {
        return client.listAll(AiMemoryAsset.class, ListOptions.builder().build(), defaultSort())
                .filter(asset -> asset.getMetadata() != null && asset.getSpec() != null)
                .collectList();
    }

    private Mono<Set<String>> resolveTeams(String userId, @Nullable String team) {
        if (StringUtils.hasText(team)) {
            return requireTeam(userId, team).thenReturn(Set.of(team));
        }
        return Mono.zip(
                        loadAssets(),
                        assetRepository.findScopes(null).collectList(),
                        assetRepository.findAgentScopes(null).collectList())
                .flatMap(tuple -> {
                    var teams = new LinkedHashSet<String>();
                    tuple.getT1().forEach(asset -> teams.add(asset.getSpec().getTeam()));
                    tuple.getT2().forEach(scope -> teams.add(scope.teamId()));
                    tuple.getT3().forEach(scope -> teams.add(scope.teamId()));
                    return Flux.fromIterable(teams)
                            .filter(StringUtils::hasText)
                            .flatMap(teamName -> teamService
                                    .isMember(teamName, userId)
                                    .filter(Boolean.TRUE::equals)
                                    .map(member -> teamName))
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                });
    }

    private Mono<List<String>> ownedAgents(String userId, Set<String> teams) {
        return client.listAll(AiAgent.class, ListOptions.builder().build(), defaultSort())
                .filter(agent -> agent.getSpec() != null
                        && agent.getMetadata() != null
                        && userId.equals(agent.getSpec().getOwnerUserId())
                        && teams.contains(agent.getSpec().getTeam()))
                .map(agent -> agent.getMetadata().getName())
                .collectList();
    }

    private static boolean agentMatches(Descriptor descriptor, @Nullable String agent) {
        if (!StringUtils.hasText(agent)) {
            return true;
        }
        var spec = descriptor.spec();
        return agent.equals(spec.getSourceAgentId())
                || (spec.getAgents() != null && spec.getAgents().contains(agent));
    }

    private static boolean scopeMatches(Descriptor descriptor, @Nullable String scope, List<String> ownedAgents) {
        if ("team".equals(scope)) {
            return descriptor.spec().getVisibility() == AiMemoryAsset.Visibility.TEAM;
        }
        if (!"fixed".equals(scope)) {
            return true;
        }
        var spec = descriptor.spec();
        return ownedAgents.contains(spec.getSourceAgentId())
                || (spec.getAgents() != null && spec.getAgents().stream().anyMatch(ownedAgents::contains));
    }

    private Mono<AssetRef> ensurePersisted(AssetRef ref) {
        if (ref.persisted()) {
            return Mono.just(ref);
        }
        var metadata = new Metadata();
        metadata.setName(ref.name());
        ref.asset().setMetadata(metadata);
        return client.create(ref.asset())
                .onErrorMap(DuplicateNameException.class, error -> conflict("资产名称已存在。"))
                .onErrorMap(DataIntegrityViolationException.class, error -> conflict("资产名称已存在。"))
                .map(created -> new AssetRef(created, true, created.getMetadata().getName()));
    }

    private Mono<MemoryAssetView> persist(AssetRef ref, AiMemoryAsset.Spec spec) {
        return ensurePersisted(ref).flatMap(persisted -> {
            persisted.asset().setSpec(spec);
            return client.update(persisted.asset())
                    .map(updated -> new AssetRef(updated, true, updated.getMetadata().getName()))
                    .flatMap(this::view);
        });
    }

    private Mono<Void> ensureUniqueAsset(
            String team, String sourceUserId, String sourceAgentId, @Nullable String excludeName) {
        return loadAssets().flatMap(assets -> {
            var duplicate = assets.stream()
                    .filter(asset -> excludeName == null
                            || !asset.getMetadata().getName().equals(excludeName))
                    .anyMatch(asset -> team.equals(asset.getSpec().getTeam())
                            && sourceUserId.equals(asset.getSpec().getSourceUserId())
                            && sourceAgentId.equals(asset.getSpec().getSourceAgentId()));
            return duplicate ? Mono.error(conflict("该记忆作用域已存在资产。")) : Mono.empty();
        });
    }

    private Mono<Void> validateAgentTargets(
            String team,
            String sourceUserId,
            List<String> agents,
            @Nullable String excludeName) {
        if (agents.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(agents)
                .concatMap(agent -> requireAgent(agent, team)
                        .then(Mono.defer(() -> validateBindingCapacity(team, sourceUserId, agent, excludeName))))
                .then();
    }

    private Mono<Void> validateBindingCapacity(
            String team, String sourceUserId, String targetAgent, @Nullable String excludeName) {
        return loadAssets().flatMap(assets -> {
            var teamAssets = assets.stream()
                    .filter(asset -> team.equals(asset.getSpec().getTeam()))
                    .filter(asset -> excludeName == null || !asset.getMetadata().getName().equals(excludeName))
                    .toList();
            var borrowed = teamAssets.stream()
                    .filter(asset -> asset.getSpec().getAgents() != null
                            && asset.getSpec().getAgents().contains(targetAgent))
                    .count();
            var persistedOwn = teamAssets.stream()
                    .anyMatch(asset -> sourceUserId.equals(asset.getSpec().getSourceUserId())
                            && targetAgent.equals(asset.getSpec().getSourceAgentId()));
            var ownCheck = persistedOwn
                    ? Mono.just(true)
                    : assetRepository.scopeExists(team, sourceUserId, targetAgent);
            return ownCheck.flatMap(ownScope -> {
                var total = borrowed + (Boolean.TRUE.equals(ownScope) ? 1 : 0);
                return total >= MAX_AGENT_BINDINGS
                        ? Mono.error(bindingLimit("同一 Agent 最多绑定两个资产。"))
                        : Mono.<Void>empty();
            });
        });
    }

    private Mono<Message> appendImported(Conversation conversation, List<ImportMessage> messages) {
        return Flux.fromIterable(messages)
                .index()
                .concatMap(tuple -> {
                    var turn = (int) tuple.getT1().longValue() + 1;
                    var message = tuple.getT2();
                    return ROLE_USER.equals(message.role())
                            ? conversations.appendUserMessage(
                                    conversation, turn, 0, message.content(), null)
                            : conversations.appendAssistantMessage(
                                    conversation, turn, 0, message.content(), null, null, null);
                })
                .collectList()
                .map(imported -> imported.get(imported.size() - 1));
    }

    private Mono<Void> requireTeam(String userId, String team) {
        return teamService
                .isMember(team, userId)
                .flatMap(member -> Boolean.TRUE.equals(member)
                        ? Mono.empty()
                        : Mono.error(forbidden("无权访问该团队。")));
    }

    private Mono<AiAgent> requireAgent(String name, String team) {
        return teamService
                .getAgent(name)
                .filter(agent -> agent.getSpec() != null && team.equals(agent.getSpec().getTeam()))
                .switchIfEmpty(Mono.error(invalid("Agent 不存在或不属于该团队。")));
    }

    private static boolean canRead(String userId, AiMemoryAsset.Spec spec) {
        return userId.equals(spec.getOwnerUserId()) || spec.getVisibility() == AiMemoryAsset.Visibility.TEAM;
    }

    private static Validation<List<String>> normalizeAgents(
            @Nullable List<String> agents, String sourceAgentId) {
        if (agents == null) {
            return Validation.ok(List.of());
        }
        var normalized = agents.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .distinct()
                .toList();
        if (normalized.size() != agents.size()) {
            return Validation.error(invalid("agents 不能包含空白或重复的 Agent。"));
        }
        if (normalized.size() > MAX_AGENTS_PER_ASSET) {
            return Validation.error(bindingLimit("每个资产最多绑定两个 Agent。"));
        }
        if (normalized.contains(sourceAgentId)) {
            return Validation.error(bindingInvalid("资产不能绑定到自身的来源 Agent。"));
        }
        return Validation.ok(normalized);
    }

    private static Validation<List<ImportMessage>> validateImport(@Nullable List<ImportMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Validation.error(importInvalid("导入消息不能为空。"));
        }
        if (messages.size() > MAX_IMPORT_MESSAGES) {
            return Validation.error(importInvalid("单次最多导入 100 条消息。"));
        }
        var normalized = new ArrayList<ImportMessage>(messages.size());
        for (var message : messages) {
            if (message == null || !StringUtils.hasText(message.content())) {
                return Validation.error(importInvalid("导入消息正文不能为空。"));
            }
            var role = message.role() == null ? "" : message.role().strip().toLowerCase(Locale.ROOT);
            if (!ROLE_USER.equals(role) && !ROLE_ASSISTANT.equals(role)) {
                return Validation.error(importInvalid("导入消息角色只能是 user 或 assistant。"));
            }
            normalized.add(new ImportMessage(role, message.content()));
        }
        return Validation.ok(List.copyOf(normalized));
    }

    private static void validateDisplayName(@Nullable String displayName) {
        if (!StringUtils.hasText(displayName)) {
            throw invalid("displayName 不能为空。");
        }
        if (displayName.strip().length() > DISPLAY_NAME_LIMIT) {
            throw invalid("displayName 长度不能超过 200。");
        }
    }

    private static void validateTeam(@Nullable String team) {
        if (!StringUtils.hasText(team)) {
            throw invalid("team 不能为空。");
        }
    }

    private static void validateSourceAgent(@Nullable String sourceAgentId) {
        if (!StringUtils.hasText(sourceAgentId)) {
            throw invalid("sourceAgentId 不能为空。");
        }
    }

    private static AiMemoryAsset.Spec copy(AiMemoryAsset.Spec spec) {
        var copy = new AiMemoryAsset.Spec();
        copy.setDisplayName(spec.getDisplayName());
        copy.setDescription(spec.getDescription());
        copy.setTeam(spec.getTeam());
        copy.setOwnerUserId(spec.getOwnerUserId());
        copy.setSourceUserId(spec.getSourceUserId());
        copy.setSourceAgentId(spec.getSourceAgentId());
        copy.setVisibility(spec.getVisibility());
        copy.setStatus(spec.getStatus());
        copy.setAutoGenerated(spec.isAutoGenerated());
        copy.setAgents(spec.getAgents() == null ? List.of() : List.copyOf(spec.getAgents()));
        copy.setCreatedByUserId(spec.getCreatedByUserId());
        return copy;
    }

    private static String scopeKey(String team, String userId, String agentId) {
        return team + "\n" + userId + "\n" + agentId;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, "记忆资产不存在。");
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, CODE_FORBIDDEN, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, CODE_CONFLICT, message);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, message);
    }

    private static ApiException bindingLimit(String message) {
        return new ApiException(HttpStatus.CONFLICT, CODE_BINDING_LIMIT, message);
    }

    private static ApiException bindingInvalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_BINDING_INVALID, message);
    }

    private static ApiException importInvalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_IMPORT_INVALID, message);
    }

    public record AssetRef(AiMemoryAsset asset, boolean persisted, String name) {

        AiMemoryAsset.Spec spec() {
            return asset.getSpec();
        }
    }

    private record Descriptor(String name, AiMemoryAsset.Spec spec, boolean auto) {

        AiMemoryAsset asset() {
            var asset = new AiMemoryAsset();
            asset.setSpec(spec);
            return asset;
        }
    }

    private record Validation<T>(T value, @Nullable ApiException error) {

        static <T> Validation<T> ok(T value) {
            return new Validation<>(value, null);
        }

        static <T> Validation<T> error(ApiException error) {
            return new Validation<>(null, error);
        }

        boolean isError() {
            return error != null;
        }
    }

    public record CreateCommand(
            @Nullable String name,
            @Nullable String generateName,
            String displayName,
            @Nullable String description,
            String team,
            @Nullable String ownerUserId,
            @Nullable String sourceUserId,
            String sourceAgentId,
            AiMemoryAsset.@Nullable Visibility visibility,
            AiMemoryAsset.@Nullable Status status,
            @Nullable List<String> agents) {}

    public record PatchCommand(
            @Nullable String displayName,
            @Nullable String description,
            AiMemoryAsset.@Nullable Visibility visibility,
            AiMemoryAsset.@Nullable Status status,
            @Nullable List<String> agents) {}

    public record ImportCommand(@Nullable List<ImportMessage> messages, @Nullable String sessionId) {}

    public record ImportMessage(String role, String content) {}

    public record ImportResult(long conversationId, int messages) {}
}