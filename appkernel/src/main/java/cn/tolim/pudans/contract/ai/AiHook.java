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
        group = AiHook.GROUP,
        version = AiHook.VERSION,
        kind = AiHook.KIND,
        plural = "aihooks",
        singular = "aihook")
public class AiHook extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiHook";

    public static final int MIN_TIMEOUT_SECONDS = 1;

    public static final int MAX_TIMEOUT_SECONDS = 600;

    private Spec spec = new Spec();

    public enum Event {
        SESSION_START,
        USER_PROMPT_SUBMIT,
        PRE_TOOL_USE,
        POST_TOOL_USE,
        SESSION_END
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private Event event;

        private String matcher;

        @Schema(requiredMode = REQUIRED)
        private String command;

        @Schema(minimum = "1", maximum = "600")
        private int timeoutSeconds = 60;

        private boolean enabled = true;

        private List<String> agents = new ArrayList<>();
    }
}