package cn.tolim.pudans.contract.ai.resource;

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
        group = AiMemoryPromptBinding.GROUP,
        version = AiMemoryPromptBinding.VERSION,
        kind = AiMemoryPromptBinding.KIND,
        plural = "aimemorypromptbindings",
        singular = "aimemorypromptbinding")
public class AiMemoryPromptBinding extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiMemoryPromptBinding";

    private Spec spec = new Spec();

    public enum TargetType {
        INSTANCE,
        TEAM,
        AGENT
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private TargetType targetType = TargetType.INSTANCE;

        private String team;

        private String agent;

        @Schema(requiredMode = REQUIRED)
        private AiMemoryPrompt.Layer layer = AiMemoryPrompt.Layer.L1;

        @Schema(requiredMode = REQUIRED)
        private String prompt;

        private String updatedBy;
    }
}
