package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.infra.r2dbc.ai.protocol.AiAgentProtocolRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AgentProtocolRunStoreTest {

    @Mock
    AiAgentProtocolRunRepository repository;

    private final AgentProtocolRunStore store = new AgentProtocolRunStore();

    @Test
    void completesARunAndIndexesItByThread() {
        var record = store.create(
                        "user-1", "run-1", "thread-1", "agent-a", new AgentRun("run-1"))
                .block();
        record.markRunning();

        store.complete("run-1", "回答").block();

        var snapshot = store.find("user-1", "run-1").block();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.COMPLETED);
        assertThat(snapshot.content()).isEqualTo("回答");
        assertThat(store.latestByThread("user-1", "thread-1").block().runId())
                .isEqualTo("run-1");
    }

    @Test
    void failedRunsKeepTheirErrorAndStayTerminal() {
        var record = store.create("user-1", "run-1", "thread-1", null, new AgentRun("run-1"))
                .block();
        record.markRunning();

        store.fail("run-1", "模型失败").block();
        store.complete("run-1", "迟到的回答").block();

        var snapshot = store.find("user-1", "run-1").block();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.FAILED);
        assertThat(snapshot.error()).isEqualTo("模型失败");
        assertThat(snapshot.content()).isNull();
    }

    @Test
    void cancelStopsTheAgentRunAndStaysTerminal() {
        var run = new AgentRun("run-1");
        store.create("user-1", "run-1", "thread-1", null, run).block();

        var snapshot = store.cancel("user-1", "run-1").block();
        store.fail("run-1", "取消后的错误").block();

        assertThat(run.cancelled()).isTrue();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.CANCELLED);
        assertThat(store.find("user-1", "run-1").block().status())
                .isEqualTo(AgentProtocolRunStatus.CANCELLED);
    }

    @Test
    void hidesRunsOwnedByAnotherUser() {
        var run = new AgentRun("run-1");
        store.create("user-1", "run-1", "thread-1", null, run).block();

        assertThat(store.find("user-2", "run-1").block()).isNull();
        assertThat(store.latestByThread("user-2", "thread-1").block()).isNull();
        assertThat(store.cancel("user-2", "run-1").block()).isNull();
        assertThat(run.cancelled()).isFalse();
    }

    @Test
    void restoresARunSnapshotFromTheRepositoryOnAnotherReplica() {
        var persisted = new AgentProtocolRunStore(repository);
        when(repository.upsert(
                        "user-1",
                        "run-1",
                        "thread-1",
                        "agent-a",
                        AgentProtocolRunStatus.PENDING.name(),
                        null,
                        null))
                .thenReturn(Mono.empty());
        var record = persisted
                .create("user-1", "run-1", "thread-1", "agent-a", new AgentRun("run-1"))
                .block();
        assertThat(record).isNotNull();
        verify(repository).upsert(
                "user-1",
                "run-1",
                "thread-1",
                "agent-a",
                AgentProtocolRunStatus.PENDING.name(),
                null,
                null);

        when(repository.find("user-1", "run-1"))
                .thenReturn(Mono.just(new AiAgentProtocolRunRepository.RunRow(
                        "user-1",
                        "run-1",
                        "thread-1",
                        "agent-a",
                        AgentProtocolRunStatus.COMPLETED.name(),
                        "回答",
                        "")));

        var snapshot = new AgentProtocolRunStore(repository).find("user-1", "run-1").block();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.COMPLETED);
        assertThat(snapshot.content()).isEqualTo("回答");
    }
}