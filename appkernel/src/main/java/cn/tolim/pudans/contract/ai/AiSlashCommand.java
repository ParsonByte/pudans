package cn.tolim.pudans.contract.ai;

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
        group = AiSlashCommand.GROUP,
        version = AiSlashCommand.VERSION,
        kind = AiSlashCommand.KIND,
        plural = "aislashcommands",
        singular = "aislashcommand")
public class AiSlashCommand extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiSlashCommand";

    public static final String NAME_PATTERN = "^[a-z0-9][a-z0-9-]*$";

    private Spec spec = new Spec();

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED, pattern = NAME_PATTERN)
        private String name;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private String prompt;

        private String argumentHint;

        private boolean enabled = true;

        private List<String> agents = new ArrayList<>();
    }
}