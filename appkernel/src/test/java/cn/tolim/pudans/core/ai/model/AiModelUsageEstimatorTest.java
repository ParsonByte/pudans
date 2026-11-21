package cn.tolim.pudans.core.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiModel;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiModelUsageEstimatorTest {

    @Test
    void estimatesImagesFromRequestedCount() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.IMAGE_GENERATION,
                List.of(new AiModelContent.Text("画一只猫")),
                Map.of("n", JsonNodeFactory.instance.numberNode(3)));

        assertThat(usage.imageCount()).isEqualTo(3);
    }

    @Test
    void defaultsToSingleImage() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.IMAGE_EDITING, List.of(), Map.of());

        assertThat(usage.imageCount()).isEqualTo(1);
    }

    @Test
    void readsCountsNestedUnderParameters() {
        var parameters = JsonNodeFactory.instance.objectNode().put("n", 4);
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.IMAGE_GENERATION,
                List.of(new AiModelContent.Text("画一只猫")),
                Map.of("parameters", parameters));

        assertThat(usage.imageCount()).isEqualTo(4);
    }

    @Test
    void estimatesVideoSecondsFromDuration() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.VIDEO_GENERATION,
                List.of(new AiModelContent.Text("海浪")),
                Map.of("duration", JsonNodeFactory.instance.numberNode(8)));

        assertThat(usage.videoSeconds()).isEqualTo(8);
        assertThat(usage.taskCount()).isNull();
    }

    @Test
    void usesConservativeVideoSecondsWhenDurationIsMissing() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.VIDEO_GENERATION, List.of(), Map.of());

        assertThat(usage.videoSeconds()).isEqualTo(10);
        assertThat(usage.taskCount()).isNull();
    }

    @Test
    void readsAudioSecondsFromInputs() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.SPEECH_RECOGNITION,
                List.of(new AiModelContent.Audio(null, null, null, 42, null, null)),
                Map.of());

        assertThat(usage.audioSeconds()).isEqualTo(42);
    }

    @Test
    void estimatesOcrAsOneImage() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.OCR, List.of(), Map.of());

        assertThat(usage.imageCount()).isEqualTo(1);
        assertThat(usage.taskCount()).isNull();
    }

    @Test
    void estimatesSpeechCharactersFromTextInputs() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.SPEECH_SYNTHESIS,
                List.of(new AiModelContent.Text("你好"), new AiModelContent.Text("世界")),
                Map.of());

        assertThat(usage.speechCharacters()).isEqualTo(4);
    }

    @Test
    void estimatesThreeDAsOneTask() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.THREE_D_GENERATION,
                List.of(new AiModelContent.Text("小房子")),
                Map.of());

        assertThat(usage.taskCount()).isEqualTo(1);
    }

    @Test
    void estimatesUnderstandingTokensFromText() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.VIDEO_UNDERSTANDING,
                List.of(new AiModelContent.Text("12345678")),
                Map.of("maxOutputTokens", JsonNodeFactory.instance.numberNode(16)));

        assertThat(usage.inputTokens()).isEqualTo(2);
        assertThat(usage.outputTokens()).isEqualTo(16);
    }

    @Test
    void leavesNonMediaCapabilitiesEmpty() {
        var usage = AiModelUsageEstimator.estimate(
                AiModel.Capability.CHAT, List.of(new AiModelContent.Text("hi")), Map.of());

        assertThat(usage.hasAny()).isFalse();
    }
}
