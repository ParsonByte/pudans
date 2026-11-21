package cn.tolim.pudans.contract.ai.resource;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AiPlanBinding.GROUP,
        version = AiPlanBinding.VERSION,
        kind = AiPlanBinding.KIND,
        plural = "aiplanbindings",
        singular = "aiplanbinding")
public class AiPlanBinding extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiPlanBinding";

    private Spec spec = new Spec();

    public enum SubjectKind {
        User,
        UserGroup
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String plan;

        @Schema(requiredMode = REQUIRED)
        private Subject subject = new Subject();

        private int priority;

        private Instant effectiveFrom;

        private Instant effectiveTo;
    }

    @Data
    public static class Subject {

        @Schema(requiredMode = REQUIRED)
        private SubjectKind kind = SubjectKind.UserGroup;

        @Schema(requiredMode = REQUIRED)
        private String name;
    }
}