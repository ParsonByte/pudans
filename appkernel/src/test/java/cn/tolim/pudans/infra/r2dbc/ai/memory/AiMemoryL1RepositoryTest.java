package cn.tolim.pudans.infra.r2dbc.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.memory.asset.MemoryL1Item;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiMemoryL1RepositoryTest {

    @Mock
    DatabaseClient databaseClient;

    @Mock
    DatabaseClient.GenericExecuteSpec spec;

    @Mock
    RowsFetchSpec<MemoryL1Item> fetchSpec;

    @Test
    void updateContentSeparatesTheTableFromTheSetClause() {
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.one()).thenReturn(Mono.empty());

        var repository = new AiMemoryL1Repository(databaseClient);

        StepVerifier.create(repository.updateContent("team-a", "agent-a", "user-1", 7L, "updated"))
                .verifyComplete();

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("UPDATE ai_memory_l1\nSET content = :content")
                .doesNotContain("ai_memory_l1SET")
                .contains("RETURNING")
                .contains("id, record_id, team_id, agent_id, user_id, session_id, task_id, conversation_id");
        verify(spec).bind("content", "updated");
        verify(spec).bind("id", 7L);
        verify(spec).bind("teamId", "team-a");
        verify(spec).bind("agentId", "agent-a");
        verify(spec).bind("userId", "user-1");
    }
}
