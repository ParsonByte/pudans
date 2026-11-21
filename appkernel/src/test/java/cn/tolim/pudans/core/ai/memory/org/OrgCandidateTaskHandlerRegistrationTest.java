package cn.tolim.pudans.core.ai.memory.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.L1Deduplication;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.ai.task.AiTaskHandlerRegistry;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiOrgMemoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrgCandidateTaskHandlerRegistrationTest {

    @Test
    void registersTheOrgCandidateTaskHandler() {
        var handler = new OrgCandidateTaskHandler(
                mock(AiMemoryL1Repository.class),
                mock(AiOrgMemoryRepository.class),
                new L1Deduplication(),
                mock(AiSettings.class));

        var registry = new AiTaskHandlerRegistry(List.of(handler));

        assertThat(handler.taskType()).isEqualTo(PipelineTaskType.ORG_CANDIDATE.name());
        assertThat(registry.find("ORG_CANDIDATE")).isSameAs(handler);
    }
}
