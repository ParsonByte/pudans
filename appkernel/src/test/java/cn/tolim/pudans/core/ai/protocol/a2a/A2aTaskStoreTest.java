package cn.tolim.pudans.core.ai.protocol.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.r2dbc.ai.protocol.AiA2aTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class A2aTaskStoreTest {

    @Mock
    AiA2aTaskRepository repository;

    @Test
    void persistsTasksAndRestoresThemOnAnotherReplica() {
        var record = new A2aTaskRecord("user-1", "task-1", "context-1");
        record.complete(A2aArtifact.ofText("artifact-1", "回答"));
        when(repository.upsert(record)).thenReturn(Mono.empty());
        new A2aTaskStore(repository).save(record).block();
        verify(repository).upsert(record);

        var artifact = JsonUtils.objectToJson(record.artifact());
        when(repository.find("user-1", "task-1"))
                .thenReturn(Mono.just(new AiA2aTaskRepository.TaskRow(
                        "context-1", A2aTaskStatus.COMPLETED, artifact)));

        var restored = new A2aTaskStore(repository).find("user-1", "task-1").block();

        assertThat(restored).isNotNull();
        assertThat(restored.status().state()).isEqualTo(A2aTaskStatus.COMPLETED);
        assertThat(restored.artifact()).isEqualTo(record.artifact());
    }
}