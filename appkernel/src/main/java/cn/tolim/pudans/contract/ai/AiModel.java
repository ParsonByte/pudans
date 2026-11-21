package cn.tolim.pudans.contract.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AiModel.GROUP,
        version = AiModel.VERSION,
        kind = AiModel.KIND,
        plural = "aimodels",
        singular = "aimodel")
public class AiModel extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiModel";

    private Spec spec = new Spec();

    public enum ModelType {
        CHAT,
        EMBEDDING,
        RERANK,
        IMAGE,
        VIDEO,
        SPEECH,
        AUDIO,
        MUSIC,
        THREE_D
    }

    public enum Modality {
        TEXT,
        IMAGE,
        AUDIO,
        VIDEO,
        FILE,
        THREE_D
    }

    public enum Capability {
        CHAT,
        TOOLS,
        JSON,
        VISION,
        AUDIO_UNDERSTANDING,
        VIDEO_UNDERSTANDING,
        EMBEDDING,
        RERANK,
        IMAGE_GENERATION,
        IMAGE_EDITING,
        VIDEO_GENERATION,
        SPEECH_RECOGNITION,
        SPEECH_SYNTHESIS,
        AUDIO_GENERATION,
        MUSIC_GENERATION,
        THREE_D_GENERATION,
        OCR,
        STREAM
    }

    public enum Tier {
        // AUTO 只用于对话模式/路由组默认档位。
        AUTO,
        CHEAP,
        MEDIUM,
        HIGH,
        ULTRA
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String provider;

        @Schema(requiredMode = REQUIRED)
        private String modelCode;

        private String displayName;

        @Schema(requiredMode = REQUIRED)
        private ModelType modelType = ModelType.CHAT;

        private Tier tier;

        private Integer contextWindow;

        private Integer maxOutputTokens;

        private boolean supportsStream = true;

        private boolean supportsTools;

        private boolean supportsJson;

        private boolean supportsVision;

        private boolean supportsAsync;

        private List<Modality> inputModalities = new ArrayList<>();

        private List<Modality> outputModalities = new ArrayList<>();

        private List<Capability> capabilities = new ArrayList<>();

        private Integer embeddingDim;

        private Integer maxBatchSize = 32;

        private Integer maxInputImages;

        private Integer maxInputSeconds;

        private Integer maxOutputItems;

        private boolean enabled = true;

        public Set<Capability> effectiveCapabilities() {
            if (capabilities != null && !capabilities.isEmpty()) {
                return EnumSet.copyOf(capabilities);
            }
            var derived = EnumSet.noneOf(Capability.class);
            switch (modelType) {
                case CHAT -> {
                    derived.add(Capability.CHAT);
                    if (supportsStream) {
                        derived.add(Capability.STREAM);
                    }
                    if (supportsTools) {
                        derived.add(Capability.TOOLS);
                    }
                    if (supportsJson) {
                        derived.add(Capability.JSON);
                    }
                    if (supportsVision) {
                        derived.add(Capability.VISION);
                    }
                }
                case EMBEDDING -> derived.add(Capability.EMBEDDING);
                case RERANK -> derived.add(Capability.RERANK);
                case IMAGE -> {
                    derived.add(Capability.IMAGE_GENERATION);
                    derived.add(Capability.IMAGE_EDITING);
                    derived.add(Capability.OCR);
                }
                case VIDEO -> derived.add(Capability.VIDEO_GENERATION);
                case SPEECH -> {
                    derived.add(Capability.SPEECH_RECOGNITION);
                    derived.add(Capability.SPEECH_SYNTHESIS);
                }
                case AUDIO -> {
                    derived.add(Capability.AUDIO_GENERATION);
                    derived.add(Capability.AUDIO_UNDERSTANDING);
                }
                case MUSIC -> derived.add(Capability.MUSIC_GENERATION);
                case THREE_D -> derived.add(Capability.THREE_D_GENERATION);
            }
            return derived;
        }

        public List<Modality> effectiveInputModalities() {
            if (inputModalities != null && !inputModalities.isEmpty()) {
                return List.copyOf(inputModalities);
            }
            return switch (modelType) {
                case CHAT -> supportsVision
                        ? List.of(Modality.TEXT, Modality.IMAGE)
                        : List.of(Modality.TEXT);
                case EMBEDDING, RERANK -> List.of(Modality.TEXT);
                case IMAGE, VIDEO, MUSIC, AUDIO, THREE_D -> List.of(Modality.TEXT, Modality.IMAGE);
                case SPEECH -> List.of(Modality.AUDIO, Modality.TEXT);
            };
        }

        public List<Modality> effectiveOutputModalities() {
            if (outputModalities != null && !outputModalities.isEmpty()) {
                return List.copyOf(outputModalities);
            }
            return switch (modelType) {
                case IMAGE -> List.of(Modality.IMAGE);
                case VIDEO -> List.of(Modality.VIDEO);
                case SPEECH -> List.of(Modality.TEXT, Modality.AUDIO);
                case AUDIO, MUSIC -> List.of(Modality.AUDIO);
                case THREE_D -> List.of(Modality.THREE_D);
                case CHAT, EMBEDDING, RERANK -> List.of(Modality.TEXT);
            };
        }
    }
}
