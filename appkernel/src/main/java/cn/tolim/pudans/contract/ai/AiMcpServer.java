package cn.tolim.pudans.contract.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
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
        group = AiMcpServer.GROUP,
        version = AiMcpServer.VERSION,
        kind = AiMcpServer.KIND,
        plural = "aimcpservers",
        singular = "aimcpserver")
public class AiMcpServer extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiMcpServer";

    private Spec spec = new Spec();

    private Status status = new Status();

    public enum Transport {
        STDIO,
        HTTP,
        SSE,
        WEBSOCKET
    }

    public enum Health {
        UNKNOWN,
        HEALTHY,
        UNHEALTHY
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private Transport transport = Transport.STDIO;

        private String command;

        private List<String> args = new ArrayList<>();

        private String url;

        private Map<String, String> headers = new LinkedHashMap<>();

        private Map<String, String> env = new LinkedHashMap<>();

        private List<String> toolAllowlist = new ArrayList<>();

        private String toolsJson;

        private boolean enabled = true;

        private List<String> agents = new ArrayList<>();
    }

    @Data
    public static class Status {

        private Health health = Health.UNKNOWN;

        private int toolCount;

        private Instant lastCheckedAt;

        private String message;
    }
}