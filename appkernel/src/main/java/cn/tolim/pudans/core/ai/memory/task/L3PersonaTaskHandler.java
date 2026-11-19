package cn.tolim.pudans.core.ai.memory.task;

import cn.tolim.pudans.contract.core.extension.ai.AiMemoryPrompt;
import cn.tolim.pudans.core.ai.memory.JevChangeType;
import cn.tolim.pudans.core.ai.memory.JevGate;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.L3PipelineService;
import cn.tolim.pudans.core.ai.memory.MemoryGenerationInput;
import cn.tolim.pudans.core.ai.memory.MemoryPromptContext;
import cn.tolim.pudans.core.ai.memory.MemoryPromptInput;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.PersonaTrigger;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.ai.memory.ai.MemoryJevAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemoryPersonaAiService;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.core.ai.task.AiTaskHandler;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class L3PersonaTaskHandler implements AiTaskHandler {

    public static final String TASK_TYPE = PipelineTaskType.L3_PERSONA.name();

    private final AiConversationRepository conversationRepository;

    private final AiMemoryL1Repository memoryL1Repository;

    private final AiMemoryL2Repository memoryL2Repository;

    private final AiMemoryL3Repository memoryL3Repository;

    private final MemoryPromptResolver promptResolver;

    private final MemoryJevAiService jevAi;

    private final MemoryPersonaAiService personaAi;

    private final L3PipelineService pipelineService;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Mono<Void> handle(AiTask task) {
        var conversationId = MemoryTaskPayloads.conversationId(task);
        if (conversationId == null) {
            return process(task, null).then();
        }
        return conversationRepository
                .findById(conversationId)
                .flatMap(conversation -> process(task, conversation))
                .then();
    }

    private Mono<Void> process(AiTask task, @Nullable Conversation conversation) {
        var teamId = MemoryTaskPayloads.teamId(task, conversation);
        var agentId = MemoryTaskPayloads.agentId(task, conversation);
        if (teamId == null || agentId == null) {
            return Mono.error(new IllegalArgumentException("L3 任务缺少团队或 Agent 标识"));
        }
        var afterL1Id = MemoryTaskPayloads.afterL1Id(task, null);
        var limit = MemoryTaskPayloads.batchLimit(task);
        return Mono.zip(
                        memoryL3Repository
                                .findByScope(teamId, agentId)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        memoryL2Repository.findByScope(teamId, agentId).collectList(),
                        memoryL1Repository.findActiveByScope(teamId, agentId, afterL1Id, limit).collectList())
                .flatMap(loaded -> apply(teamId, agentId, loaded.getT1(), loaded.getT2(), loaded.getT3()));
    }

    private Mono<Void> apply(
            String teamId,
            String agentId,
            Optional<L3Persona> currentPersona,
            List<L2SceneBlock> scenes,
            List<L1MemoryRecord> loadedL1) {
        var newL1 = freshL1(currentPersona, loadedL1);
        if (newL1.isEmpty()) {
            return Mono.empty();
        }
        return changedScenes(scenes, newL1)
                .flatMap(changed -> generate(teamId, agentId, currentPersona, scenes, newL1, changed))
                .then();
    }

    private static List<L1MemoryRecord> freshL1(
            Optional<L3Persona> currentPersona, List<L1MemoryRecord> loadedL1) {
        var refreshedAt = currentPersona.map(L3Persona::lastRefreshedAt).orElse(null);
        if (refreshedAt == null) {
            return loadedL1;
        }
        return loadedL1.stream()
                .filter(record -> record.timestamps().stream().anyMatch(timestamp -> timestamp.isAfter(refreshedAt)))
                .toList();
    }

    private Mono<List<JevGate.ChangedScene>> changedScenes(
            List<L2SceneBlock> scenes, List<L1MemoryRecord> newL1) {
        var newIds = new HashSet<Long>();
        for (var record : newL1) {
            newIds.add(record.id());
        }
        var changedBlocks = scenes.stream()
                .filter(scene -> scene.l1Ids().stream().anyMatch(newIds::contains))
                .toList();
        var referencedIds = changedBlocks.stream()
                .flatMap(scene -> scene.l1Ids().stream())
                .distinct()
                .toList();
        return memoryL1Repository
                .findByIds(referencedIds)
                .collectList()
                .map(referenced -> toChangedScenes(changedBlocks, referenced));
    }

    private static List<JevGate.ChangedScene> toChangedScenes(
            List<L2SceneBlock> blocks, List<L1MemoryRecord> referenced) {
        var contentById = new HashMap<Long, String>();
        for (var record : referenced) {
            contentById.put(record.id(), record.content());
        }
        var changed = new ArrayList<JevGate.ChangedScene>(blocks.size());
        for (var block : blocks) {
            var content = block.l1Ids().stream()
                    .map(contentById::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            changed.add(new JevGate.ChangedScene(block.sceneKey(), block.summary(), content));
        }
        return List.copyOf(changed);
    }

    private Mono<Void> generate(
            String teamId,
            String agentId,
            Optional<L3Persona> currentPersona,
            List<L2SceneBlock> scenes,
            List<L1MemoryRecord> newL1,
            List<JevGate.ChangedScene> changedScenes) {
        var systemPrompt = MemoryPromptContext
                .compose(promptResolver, AiMemoryPrompt.Layer.L3, MemoryPromptInput.none())
                .systemPrompt();
        var personaText = currentPersona.map(L3Persona::personaText).orElse(null);
        var labels = Flux.fromIterable(changedScenes)
                .concatMap(scene -> jevAi
                        .classify(personaText, scene)
                        .map(label -> Map.entry(scene.sceneKey(), label)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
        var generated = personaAi.generate(systemPrompt, currentPersona.orElse(null), changedScenes);
        var state = new PersonaTrigger.State(
                false,
                currentPersona.isPresent(),
                hasText(personaText),
                !scenes.isEmpty(),
                scenes.size(),
                newL1.size(),
                MemoryTaskPayloads.PERSONA_THRESHOLD);
        return Mono.zip(labels, generated)
                .flatMap(precomputed -> pipelineService.process(
                        new L3PipelineService.Request(
                                teamId,
                                agentId,
                                state,
                                changedScenes,
                                MemoryPromptInput.none(),
                                MemoryGenerationInput.empty()),
                        (current, scene) -> precomputed
                                .getT1()
                                .getOrDefault(scene.sceneKey(), JevChangeType.NONE),
                        (prompt, current, changed) -> precomputed.getT2()))
                .then();
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
