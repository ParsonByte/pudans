package cn.tolim.pudans.core.ai.model;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AiModelUsageEstimator {

    private AiModelUsageEstimator() {}

    public static AiModelUsage estimate(
            AiModel.Capability capability,
            List<AiModelContent> inputs,
            Map<String, JsonNode> params) {
        var images = intParam(params, "n", intParam(params, "imageCount", 1));
        var seconds = intParam(
                params, "durationSeconds", intParam(params, "duration", inputSeconds(inputs)));
        var characters = textLength(inputs);
        return switch (capability) {
            case IMAGE_GENERATION, IMAGE_EDITING -> new AiModelUsage(
                    null, null, null, null, Math.max(1, images), null, null, null, null, Map.of());
            case VIDEO_GENERATION -> new AiModelUsage(
                    null, null, null, null, null, seconds > 0 ? seconds : 10,
                    null, null, null, Map.of());
            case SPEECH_SYNTHESIS -> new AiModelUsage(
                    null, null, null, null, null, null, null, Math.max(1, characters), null, Map.of());
            case SPEECH_RECOGNITION -> new AiModelUsage(
                    null, null, null, null, null, null, seconds > 0 ? seconds : 10,
                    null, null, Map.of());
            case AUDIO_GENERATION, MUSIC_GENERATION -> new AiModelUsage(
                    null, null, null, null, null, null, seconds > 0 ? seconds : 60,
                    null, null, Map.of());
            case OCR -> new AiModelUsage(
                    null, null, null, null, 1, null, null, null, null, Map.of());
            case THREE_D_GENERATION -> new AiModelUsage(
                    null, null, null, null, null, null, null, null, 1, Map.of());
            case VISION, AUDIO_UNDERSTANDING, VIDEO_UNDERSTANDING -> new AiModelUsage(
                    Math.max(1, (textLength(inputs) + 3) / 4),
                    intParam(params, "maxOutputTokens",
                            intParam(params, "maxTokens",
                                    intParam(params, "max_output_tokens", 1024))),
                    null, null, null, null, null, null, null, Map.of());
            default -> AiModelUsage.EMPTY;
        };
    }

    private static int inputSeconds(List<AiModelContent> inputs) {
        var seconds = 0;
        for (var input : inputs) {
            if (input instanceof AiModelContent.Audio audio && audio.durationSeconds() != null) {
                seconds += audio.durationSeconds();
            } else if (input instanceof AiModelContent.Video video && video.durationSeconds() != null) {
                seconds += video.durationSeconds();
            }
        }
        return seconds;
    }

    public static int textLength(List<AiModelContent> inputs) {
        var length = 0;
        if (inputs == null) {
            return 0;
        }
        for (var input : inputs) {
            if (input instanceof AiModelContent.Text text) {
                length += text.text().length();
            }
        }
        return length;
    }

    private static int intParam(Map<String, JsonNode> params, String name, int fallback) {
        var value = param(params, name);
        return value != null && value.isIntegralNumber() ? value.asInt() : fallback;
    }

    private static @Nullable JsonNode param(Map<String, JsonNode> params, String name) {
        if (params == null) {
            return null;
        }
        var value = params.get(name);
        if (value != null) {
            return value;
        }
        var nested = params.get("parameters");
        return nested != null && nested.isObject() ? nested.get(name) : null;
    }
}
