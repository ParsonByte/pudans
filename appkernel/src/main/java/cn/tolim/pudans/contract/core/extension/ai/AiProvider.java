package cn.tolim.pudans.contract.core.extension.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
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
        group = AiProvider.GROUP,
        version = AiProvider.VERSION,
        kind = AiProvider.KIND,
        plural = "aiproviders",
        singular = "aiprovider")
public class AiProvider extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiProvider";

    private Spec spec = new Spec();

    private Status status = new Status();

    public enum Protocol {
        OPENAI_CHAT,
        OPENAI_RESPONSES,
        ANTHROPIC,
        DEEPSEEK,
        DEEPSEEK_CHAT,
        DEEPSEEK_RESPONSES,
        MINIMAX,
        MINIMAX_CHAT,
        MINIMAX_NATIVE,
        MINIMAX_RESPONSES,
        QIANWEN,
        GEMINI,
        GEMINI_OPENAI,
        OLLAMA,
        CUSTOM_HTTP
    }

    public enum Routing {
        SMART,
        ORDER,
        ROTATE,
        USAGE,
        PACE,
        WEIGHT,
        FREE,
        FAST,
        CHEAP,
        DRAIN,
        EXPIRING
    }

    public enum Affinity {
        AUTO,
        SESSION,
        TURN,
        OFF,
        STRICT
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        @Schema(requiredMode = REQUIRED)
        private Protocol protocol;

        private String baseUrl;

        private Map<String, String> headers = new LinkedHashMap<>();

        private Integer connectTimeoutMs = 5000;

        private Integer readTimeoutMs = 60000;

        private Integer maxConcurrency = 100;

        private Integer maxRPM = 0;

        private Routing routing = Routing.SMART;

        private Affinity affinity = Affinity.AUTO;

        private List<String> fallback = new ArrayList<>();

        private boolean free;

        private boolean enabled = true;
    }

    @Data
    public static class Status {

        private Double healthScore;

        private Instant lastTestedAt;

        private String message;
    }
}
