package cn.tolim.pudans.core.ai.memory.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.conversation.Conversations;
import cn.tolim.pudans.core.ai.memory.L1Deduplication;
import cn.tolim.pudans.core.ai.memory.L1Pipeline;
import cn.tolim.pudans.core.ai.memory.L2Pipeline;
import cn.tolim.pudans.core.ai.memory.L3Pipeline;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.ai.memory.ai.MemoryConflict;
import cn.tolim.pudans.core.ai.memory.ai.MemoryExtraction;
import cn.tolim.pudans.core.ai.memory.ai.JevClassification;
import cn.tolim.pudans.core.ai.memory.ai.PersonaProfiling;
import cn.tolim.pudans.core.ai.memory.ai.SceneSegmentation;
import cn.tolim.pudans.core.ai.task.AiTaskHandlerRegistry;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemoryTaskHandlerRegistryTest {

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    Conversations conversationService;

    @Mock
    AiMessageRepository messageRepository;

    @Mock
    AiMemoryL1Repository memoryL1Repository;

    @Mock
    AiMemoryL2Repository memoryL2Repository;

    @Mock
    AiMemoryL3Repository memoryL3Repository;

    @Mock
    AiSettings settingService;

    @Mock
    MemoryExtraction extractionAi;

    @Mock
    MemoryConflict conflictAi;

    @Mock
    SceneSegmentation sceneAi;

    @Mock
    JevClassification jevAi;

    @Mock
    PersonaProfiling personaAi;

    @Mock
    L1Pipeline l1Pipeline;

    @Mock
    L2Pipeline l2Pipeline;

    @Mock
    L3Pipeline l3Pipeline;

    @Test
    void registersEachMemoryPipelineTaskTypeExactlyOnce() {
        var l1 = new L1ExtractTaskHandler(
                conversationRepository,
                conversationService,
                messageRepository,
                memoryL1Repository,
                memoryL2Repository,
                memoryL3Repository,
                settingService,
                extractionAi,
                conflictAi,
                new L1Deduplication(),
                l1Pipeline);
        var l2 = new L2SceneTaskHandler(
                conversationRepository,
                memoryL1Repository,
                memoryL2Repository,
                new MemoryPromptResolver(),
                sceneAi,
                l2Pipeline);
        var l3 = new L3PersonaTaskHandler(
                conversationRepository,
                memoryL1Repository,
                memoryL2Repository,
                memoryL3Repository,
                new MemoryPromptResolver(),
                jevAi,
                personaAi,
                l3Pipeline);
        var registry = new AiTaskHandlerRegistry(List.of(l1, l2, l3));

        assertThat(registry.find(PipelineTaskType.L1_EXTRACT.name())).isSameAs(l1);
        assertThat(registry.find(PipelineTaskType.L2_SCENE.name())).isSameAs(l2);
        assertThat(registry.find(PipelineTaskType.L3_PERSONA.name())).isSameAs(l3);
        assertThat(List.of(l1.taskType(), l2.taskType(), l3.taskType()))
                .containsExactly(
                        PipelineTaskType.L1_EXTRACT.name(),
                        PipelineTaskType.L2_SCENE.name(),
                        PipelineTaskType.L3_PERSONA.name());
        assertThatThrownBy(() -> new AiTaskHandlerRegistry(List.of(l1, l1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }
}
