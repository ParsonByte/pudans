package cn.tolim.pudans.contract.core.extension.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AiInstruction.GROUP,
        version = AiInstruction.VERSION,
        kind = AiInstruction.KIND,
        plural = "aiinstructions",
        singular = "aiinstruction")
public class AiInstruction extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiInstruction";

    private Spec spec = new Spec();

    public enum Scope {
        SHARED,
        AGENT
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        @Schema(requiredMode = REQUIRED)
        private String content;

        @Schema(requiredMode = REQUIRED)
        private Scope scope = Scope.SHARED;

        private boolean enabled = true;

        private List<String> agents = new ArrayList<>();
    }
}