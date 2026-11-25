package cn.tolim.pudans.contract.creation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerationTaskTest {

    @Test
    void allowsOnlyTheDocumentedStatusTransitions() {
        assertThat(GenerationTask.Status.QUEUED.canTransitionTo(GenerationTask.Status.DISPATCHING))
                .isTrue();
        assertThat(GenerationTask.Status.DISPATCHING.canTransitionTo(
                        GenerationTask.Status.PROVIDER_RUNNING))
                .isTrue();
        assertThat(GenerationTask.Status.PROVIDER_RUNNING.canTransitionTo(
                        GenerationTask.Status.DOWNLOADING))
                .isTrue();
        assertThat(GenerationTask.Status.DOWNLOADING.canTransitionTo(
                        GenerationTask.Status.ARCHIVING))
                .isTrue();
        assertThat(GenerationTask.Status.ARCHIVING.canTransitionTo(GenerationTask.Status.SUCCEEDED))
                .isTrue();
        assertThat(GenerationTask.Status.FAILED.canTransitionTo(GenerationTask.Status.RETRY_QUEUED))
                .isTrue();
        assertThat(GenerationTask.Status.ARCHIVING.canTransitionTo(GenerationTask.Status.CANCELLED))
                .isFalse();
        assertThat(GenerationTask.Status.SUCCEEDED.canTransitionTo(GenerationTask.Status.FAILED))
                .isFalse();
    }

    @Test
    void reportsTerminalStatesAndStableCodes() {
        assertThat(GenerationTask.Status.SUCCEEDED.terminal()).isTrue();
        assertThat(GenerationTask.Status.FAILED.terminal()).isTrue();
        assertThat(GenerationTask.Status.CANCELLED.terminal()).isTrue();
        assertThat(GenerationTask.Status.PROVIDER_RUNNING.terminal()).isFalse();
        assertThat(GenerationTask.MediaType.IMAGE.getCode()).isEqualTo("image");
        assertThat(GenerationTask.Operation.LIP_SYNC.getCode()).isEqualTo("lip_sync");
    }

    @Test
    void parsesStableCodesAndRejectsUnknownValues() {
        assertThat(GenerationTask.MediaType.fromCode("video"))
                .isEqualTo(GenerationTask.MediaType.VIDEO);
        assertThat(GenerationTask.Operation.fromCode("frame"))
                .isEqualTo(GenerationTask.Operation.FRAME);
        assertThat(GenerationTask.Status.fromCode("retry_queued"))
                .isEqualTo(GenerationTask.Status.RETRY_QUEUED);
        assertThat(GenerationTask.AttemptStatus.fromCode("requesting"))
                .isEqualTo(GenerationTask.AttemptStatus.REQUESTING);
        assertThatThrownBy(() -> GenerationTask.Status.fromCode("lost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotsMutableSettingsAndInputs() {
        var settings = new java.util.LinkedHashMap<String, Object>();
        settings.put("ratio", "16:9");
        var inputs = new java.util.ArrayList<>(List.of("asset-1"));
        var task = task(settings, inputs);

        settings.put("ratio", "1:1");
        inputs.add("asset-2");

        assertThat(task.settings()).containsEntry("ratio", "16:9");
        assertThat(task.inputAssetIds()).containsExactly("asset-1");
    }

    private static GenerationTask task(
            Map<String, Object> settings, List<String> inputAssetIds) {
        return new GenerationTask(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                null,
                null,
                null,
                null,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                UUID.randomUUID(),
                "prompt",
                settings,
                inputAssetIds,
                GenerationTask.Status.QUEUED,
                false,
                null,
                List.of(),
                null,
                null,
                null,
                "idempotency",
                Instant.parse("2026-11-25T08:00:00Z"),
                null,
                null);
    }
}
