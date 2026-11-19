package cn.tolim.pudans.infra.r2dbc.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiMemoryL1RepositoryOrgCandidateTest {

    @Mock
    DatabaseClient databaseClient;

    @Mock
    DatabaseClient.GenericExecuteSpec spec;

    @Mock
    RowsFetchSpec<L1MemoryRecord> fetchSpec;

    @Test
    void queriesActiveOrgCandidatesAfterTheWatermark() {
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.empty());

        var repository = new AiMemoryL1Repository(databaseClient);

        StepVerifier.create(repository.findOrgCandidates("team-a", "agent-a", 42L, 100))
                .verifyComplete();

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("org_candidate = TRUE")
                .contains("status = 'ACTIVE'")
                .contains("team_id = :teamId")
                .contains("agent_id = :agentId")
                .contains("id >= :afterId");
        verify(spec).bind("teamId", "team-a");
        verify(spec).bind("agentId", "agent-a");
        verify(spec).bind("afterId", 42L);
        verify(spec).bind("limit", 100);
    }
}
