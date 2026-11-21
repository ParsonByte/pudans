package cn.tolim.pudans.contract.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
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
        group = AiSubagent.GROUP,
        version = AiSubagent.VERSION,
        kind = AiSubagent.KIND,
        plural = "aisubagents",
        singular = "aisubagent")
public class AiSubagent extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiSubagent";

    private Spec spec = new Spec();

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private String prompt;

        private String routeGroup;

        private String model;

        private List<String> skills = new ArrayList<>();

        private List<String> mcpServers = new ArrayList<>();

        private boolean enabled = true;

        private List<String> agents = new ArrayList<>();
    }
}