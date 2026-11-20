package cn.tolim.pudans.core.ai.harness.tool;

import static cn.tolim.pudans.core.ai.harness.tool.AsyncToolTestSupport.await;
import static cn.tolim.pudans.core.ai.harness.tool.AsyncToolTestSupport.awaitTerminal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryAsyncToolRegistryTest {

    private final InMemoryAsyncToolRegistry registry = new InMemoryAsyncToolRegistry();

    @Test
    void submitsTasksThatTransitionFromRunningToCompleted() {
        var release = new CountDownLatch(1);
        var submitted = registry.submit("analyze", () -> {
            await(release);
            return new HarnessToolkit.ToolResult("analysis-result", false);
        }).block();

        assertThat(submitted.status()).isEqualTo(AsyncToolStatus.RUNNING);
        assertThat(registry.read(submitted.id()).block().status()).isEqualTo(AsyncToolStatus.RUNNING);

        release.countDown();
        var completed = awaitTerminal(registry, submitted.id());

        assertThat(completed.status()).isEqualTo(AsyncToolStatus.COMPLETED);
        assertThat(completed.output()).isEqualTo("analysis-result");
        assertThat(completed.error()).isEmpty();
    }

    @Test
    void recordsExecutionFailures() {
        var task = registry.submit("broken", () -> {
            throw new IllegalStateException("boom");
        }).block();

        var failed = awaitTerminal(registry, task.id());

        assertThat(failed.status()).isEqualTo(AsyncToolStatus.FAILED);
        assertThat(failed.error()).contains("boom");
    }

    @Test
    void mapsErrorResultsToFailedTasks() {
        var task = registry.submit("denied", () -> new HarnessToolkit.ToolResult("denied", true))
                .block();

        var failed = awaitTerminal(registry, task.id());

        assertThat(failed.status()).isEqualTo(AsyncToolStatus.FAILED);
        assertThat(failed.error()).isEqualTo("denied");
    }

    @Test
    void cancelsRunningTasksAndIgnoresLateResults() {
        var release = new CountDownLatch(1);
        var task = registry.submit("slow", () -> {
            await(release);
            return new HarnessToolkit.ToolResult("late", false);
        }).block();

        assertThat(registry.cancel(task.id()).block()).isTrue();
        assertThat(registry.read(task.id()).block().status()).isEqualTo(AsyncToolStatus.CANCELLED);
        assertThat(registry.cancel(task.id()).block()).isFalse();

        release.countDown();
        assertThat(awaitTerminal(registry, task.id()).status()).isEqualTo(AsyncToolStatus.CANCELLED);
    }

    @Test
    void listsTasksInSubmissionOrder() {
        var first = registry
                .submit("one", () -> new HarnessToolkit.ToolResult("1", false))
                .block();
        var second = registry
                .submit("two", () -> new HarnessToolkit.ToolResult("2", false))
                .block();

        assertThat(registry.list().block())
                .extracting(AsyncToolTask::id)
                .containsExactly(first.id(), second.id());
    }

    @Test
    void returnsEmptyResultsForUnknownTasks() {
        StepVerifier.create(registry.read("missing")).verifyComplete();

        assertThat(registry.cancel("missing").block()).isFalse();
        assertThat(registry.list().block()).isEmpty();
    }

    @Test
    void rejectsMissingSubmissionParts() {
        assertThatThrownBy(() -> registry.submit(null, () -> new HarnessToolkit.ToolResult("", false)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.submit("tool", null))
                .isInstanceOf(NullPointerException.class);
    }
}