package cn.tolim.pudans.contract.ai;

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

        private Harness harness = new Harness();
    }

    @Data
    public static class Harness {

        public static final int DEFAULT_COMPACTION_TRIGGER_MESSAGES = 80;

        public static final int DEFAULT_COMPACTION_KEEP_MESSAGES = 20;

        public static final int DEFAULT_TOOL_RESULT_EVICTION_MAX_CHARS = 30000;

        public static final int DEFAULT_CONFIRMATION_TIMEOUT_SECONDS = 300;

        private boolean planModeEnabled;

        private boolean todosEnabled = true;

        private int compactionTriggerMessages = DEFAULT_COMPACTION_TRIGGER_MESSAGES;

        private int compactionKeepMessages = DEFAULT_COMPACTION_KEEP_MESSAGES;

        private int toolResultEvictionMaxChars = DEFAULT_TOOL_RESULT_EVICTION_MAX_CHARS;

        private int confirmationTimeoutSeconds = DEFAULT_CONFIRMATION_TIMEOUT_SECONDS;

        private boolean toolSearchEnabled = true;

        private boolean toolGroupsEnabled = true;

        private List<String> asyncToolPatterns = new ArrayList<>();

        private List<String> externalToolPatterns = new ArrayList<>();

        private boolean workspaceEnabled = true;

        private boolean memoryToolsEnabled = true;

        private PermissionMode permissionMode = PermissionMode.BYPASS;

        private List<PermissionRule> permissionRules = new ArrayList<>();

        public enum PermissionMode {
            BYPASS,
            DEFAULT,
            ACCEPT_EDITS,
            EXPLORE,
            DONT_ASK
        }

        public enum PermissionBehavior {
            ALLOW,
            DENY,
            ASK
        }

        @Data
        public static class PermissionRule {

            private String toolName;

            private String ruleContent;

            private PermissionBehavior behavior = PermissionBehavior.ASK;
        }
    }

    public enum RuntimeProvider {
        NONE,
        ALIYUN,
        TENCENT,
        E2B
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
