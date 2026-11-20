package cn.tolim.pudans.contract.core.extension.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AiAgent.GROUP,
        version = AiAgent.VERSION,
        kind = AiAgent.KIND,
        plural = "aiagents",
        singular = "aiagent")
public class AiAgent extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiAgent";

    private Spec spec = new Spec();

    public enum AgentStatus {
        ACTIVE,
        INACTIVE
    }

    public enum Visibility {
        PRIVATE,
        TEAM,
        RESTRICTED
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String team;

        @Schema(requiredMode = REQUIRED)
        private String ownerUserId;

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        private String prompt;

        private Visibility visibility = Visibility.TEAM;

        private AgentStatus status = AgentStatus.ACTIVE;

        private String defaultRouteGroup;

        private String defaultChatMode;

        private List<String> skills = new ArrayList<>();

        private List<String> mcpServers = new ArrayList<>();

        private List<String> hooks = new ArrayList<>();

        private List<String> environmentVariables = new ArrayList<>();

        private List<String> subagents = new ArrayList<>();

        private List<String> slashCommands = new ArrayList<>();

        private List<String> instructions = new ArrayList<>();

        private Runtime runtime = new Runtime();

        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    public enum RuntimeProvider {
        NONE,
        ALIYUN,
        TENCENT
    }

    @Data
    public static class Runtime {

        private RuntimeProvider provider = RuntimeProvider.NONE;

        private String image;

        private String entrypoint;

        private Double cpu = 1.0;

        private Integer memoryMb = 1024;

        private Integer timeoutSeconds = 3600;

        private boolean autoStart;

        private boolean networkEnabled = true;

        private String workdir = "/workspace";
    }
}
