package cn.tolim.pudans.core.ai.memory.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.memory.L1DedupService;
import cn.tolim.pudans.core.ai.memory.L1PipelineService;
import cn.tolim.pudans.core.ai.memory.L2PipelineService;
import cn.tolim.pudans.core.ai.memory.L3PipelineService;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.ai.memory.ai.MemoryConflictAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemoryExtractionAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemoryJevAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemoryPersonaAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemorySceneAiService;
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
    AiConversationService conversationService;

    @Mock
    AiMessageRepository messageRepository;

    @Mock
    AiMemoryL1Repository memoryL1Repository;

    @Mock
    AiMemoryL2Repository memoryL2Repository;

    @Mock
    AiMemoryL3Repository memoryL3Repository;

    @Mock
    AiSettingService settingService;

    @Mock
    MemoryExtractionAiService extractionAi;

    @Mock
    MemoryConflictAiService conflictAi;

    @Mock
    MemorySceneAiService sceneAi;

    @Mock
    MemoryJevAiService jevAi;

    @Mock
    MemoryPersonaAiService personaAi;

    @Mock
    L1PipelineService l1PipelineService;

    @Mock
    L2PipelineService l2PipelineService;

    @Mock
    L3PipelineService l3PipelineService;

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
                new L1DedupService(),
                l1PipelineService);
        var l2 = new L2SceneTaskHandler(
                conversationRepository,
                memoryL1Repository,
                memoryL2Repository,
                new MemoryPromptResolver(),
                sceneAi,
                l2PipelineService);
        var l3 = new L3PersonaTaskHandler(
                conversationRepository,
                memoryL1Repository,
                memoryL2Repository,
                memoryL3Repository,
                new MemoryPromptResolver(),
                jevAi,
                personaAi,
                l3PipelineService);
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
