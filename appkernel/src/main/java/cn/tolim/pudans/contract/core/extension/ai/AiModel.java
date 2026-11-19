package cn.tolim.pudans.contract.core.extension.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
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
        RERANK
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

        private Integer embeddingDim;

        private Integer maxBatchSize = 32;

        private int inputTokensPerPoint = 1000;

        private int cacheTokensPerPoint = 1000;

        private int outputTokensPerPoint = 1000;

        private boolean enabled = true;
    }
}
