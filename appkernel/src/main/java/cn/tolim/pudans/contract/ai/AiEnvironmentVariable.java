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
        group = AiEnvironmentVariable.GROUP,
        version = AiEnvironmentVariable.VERSION,
        kind = AiEnvironmentVariable.KIND,
        plural = "aienvironmentvariables",
        singular = "aienvironmentvariable")
public class AiEnvironmentVariable extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiEnvironmentVariable";

    public static final String NAME_PATTERN = "^[A-Z][A-Z0-9_]*$";

    private Spec spec = new Spec();

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED, pattern = NAME_PATTERN)
        private String name;

        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        private String value;

        private boolean configured = true;

        private String description;

        private boolean enabled = true;

        private List<String> agents = new ArrayList<>();
    }
}