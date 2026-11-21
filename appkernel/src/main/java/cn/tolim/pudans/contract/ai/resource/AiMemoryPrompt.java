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
        group = AiMemoryPrompt.GROUP,
        version = AiMemoryPrompt.VERSION,
        kind = AiMemoryPrompt.KIND,
        plural = "aimemoryprompts",
        singular = "aimemoryprompt")
public class AiMemoryPrompt extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiMemoryPrompt";

    private Spec spec = new Spec();

    public enum Layer {
        L1,
        L2,
        L3
    }

    public enum PromptStatus {
        ACTIVE,
        DELETING
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String name;

        @Schema(requiredMode = REQUIRED)
        private Layer layer = Layer.L1;

        @Schema(requiredMode = REQUIRED)
        private String prompt;

        private int version = 1;

        private PromptStatus status = PromptStatus.ACTIVE;

        private String createdBy;

        private String updatedBy;
    }
}
