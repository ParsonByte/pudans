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
        group = AiSkill.GROUP,
        version = AiSkill.VERSION,
        kind = AiSkill.KIND,
        plural = "aiskills",
        singular = "aiskill")
public class AiSkill extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiSkill";

    private Spec spec = new Spec();

    public enum Source {
        INLINE,
        URL,
        GIT
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private Source source = Source.INLINE;

        private String url;

        private String ref;

        private String path;

        private String content;

        private boolean enabled = true;

        private List<String> agents = new ArrayList<>();
    }
}