package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import org.junit.jupiter.api.Test;

class AgentProtocolRunStoreTest {

    private final AgentProtocolRunStore store = new AgentProtocolRunStore();

    @Test
    void completesARunAndIndexesItByThread() {
        var record = store.create("user-1", "run-1", "thread-1", "agent-a", new AgentRun("run-1"));
        record.markRunning();

        store.complete("run-1", "回答");

        var snapshot = store.find("user-1", "run-1").orElseThrow();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.COMPLETED);
        assertThat(snapshot.content()).isEqualTo("回答");
        assertThat(store.latestByThread("user-1", "thread-1").orElseThrow().runId())
                .isEqualTo("run-1");
    }

    @Test
    void failedRunsKeepTheirErrorAndStayTerminal() {
        var record = store.create("user-1", "run-1", "thread-1", null, new AgentRun("run-1"));
        record.markRunning();

        store.fail("run-1", "模型失败");
        store.complete("run-1", "迟到的回答");

        var snapshot = store.find("user-1", "run-1").orElseThrow();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.FAILED);
        assertThat(snapshot.error()).isEqualTo("模型失败");
        assertThat(snapshot.content()).isNull();
    }

    @Test
    void cancelStopsTheAgentRunAndStaysTerminal() {
        var run = new AgentRun("run-1");
        store.create("user-1", "run-1", "thread-1", null, run);

        var snapshot = store.cancel("user-1", "run-1").orElseThrow();
        store.fail("run-1", "取消后的错误");

        assertThat(run.cancelled()).isTrue();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.CANCELLED);
        assertThat(store.find("user-1", "run-1").orElseThrow().status())
                .isEqualTo(AgentProtocolRunStatus.CANCELLED);
    }

    @Test
    void hidesRunsOwnedByAnotherUser() {
        var run = new AgentRun("run-1");
        store.create("user-1", "run-1", "thread-1", null, run);

        assertThat(store.find("user-2", "run-1")).isEmpty();
        assertThat(store.latestByThread("user-2", "thread-1")).isEmpty();
        assertThat(store.cancel("user-2", "run-1")).isEmpty();
        assertThat(run.cancelled()).isFalse();
    }
}