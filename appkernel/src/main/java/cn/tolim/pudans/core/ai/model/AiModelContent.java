package cn.tolim.pudans.core.ai.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AiModelContent.Text.class, name = "text"),
    @JsonSubTypes.Type(value = AiModelContent.Image.class, name = "image"),
    @JsonSubTypes.Type(value = AiModelContent.Audio.class, name = "audio"),
    @JsonSubTypes.Type(value = AiModelContent.Video.class, name = "video"),
    @JsonSubTypes.Type(value = AiModelContent.File.class, name = "file"),
    @JsonSubTypes.Type(value = AiModelContent.ThreeD.class, name = "three_d"),
    @JsonSubTypes.Type(value = AiModelContent.Vector.class, name = "vector"),
    @JsonSubTypes.Type(value = AiModelContent.Json.class, name = "json")
})
public sealed interface AiModelContent {

    record Text(String text) implements AiModelContent {

        public Text {
            text = text == null ? "" : text;
        }
    }

    record Image(
            @Nullable String url,
            @Nullable String data,
            @Nullable String mediaType,
            @Nullable String detail,
            @Nullable String fileId) implements AiModelContent {}

    record Audio(
            @Nullable String url,
            @Nullable String data,
            @Nullable String mediaType,
            @Nullable Integer durationSeconds,
            @Nullable String format,
            @Nullable String fileId) implements AiModelContent {}

    record Video(
            @Nullable String url,
            @Nullable String data,
            @Nullable String mediaType,
            @Nullable Integer durationSeconds,
            @Nullable String fileId) implements AiModelContent {}

    record File(
            @Nullable String url,
            @Nullable String data,
            @Nullable String filename,
            @Nullable String mediaType,
            @Nullable String fileId) implements AiModelContent {}

    record ThreeD(
            @Nullable String url,
            @Nullable String data,
            @Nullable String mediaType,
            @Nullable String fileId) implements AiModelContent {}

    record Vector(@Nullable String model, int dimensions, float[] values) implements AiModelContent {}

    record Json(JsonNode value) implements AiModelContent {

        public Json {
            if (value == null) {
                throw new IllegalArgumentException("JSON 内容不能为空");
            }
        }
    }
}
