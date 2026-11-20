package cn.tolim.pudans.core.ai.harness.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AgentRunTest {

    @Test
    void suspendsUntilAConfirmationCompletesTheSink() throws Exception {
        var run = new AgentRun("run-1");
        var confirmation = run.awaitConfirmation("call-1").toFuture();

        assertThat(run.status()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(run.confirm("call-1", true)).isTrue();
        assertThat(confirmation.get(3, TimeUnit.SECONDS)).isTrue();
        assertThat(run.status()).isEqualTo(AgentRunStatus.RUNNING);
    }

    @Test
    void acceptsAConfirmationBeforeTheRunAsks() {
        var run = new AgentRun("run-2");

        assertThat(run.confirm("call-2", false)).isTrue();

        assertThat(run.awaitConfirmation("call-2").block()).isFalse();
    }

    @Test
    void suspendsUntilAnExternalResultArrives() throws Exception {
        var run = new AgentRun("run-3");
        var result = run.awaitExternalResult("call-3").toFuture();

        assertThat(run.status()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(run.submitExternalResult("call-3", "external-output", false)).isTrue();
        var resolved = result.get(3, TimeUnit.SECONDS);
        assertThat(resolved.output()).isEqualTo("external-output");
        assertThat(resolved.error()).isFalse();
        assertThat(run.status()).isEqualTo(AgentRunStatus.RUNNING);
    }

    @Test
    void acceptsAnExternalResultBeforeTheRunAsks() {
        var run = new AgentRun("run-4");

        assertThat(run.submitExternalResult("call-4", "failed", true)).isTrue();

        var result = run.awaitExternalResult("call-4").block();
        assertThat(result).isNotNull();
        assertThat(result.output()).isEqualTo("failed");
        assertThat(result.error()).isTrue();
    }
}