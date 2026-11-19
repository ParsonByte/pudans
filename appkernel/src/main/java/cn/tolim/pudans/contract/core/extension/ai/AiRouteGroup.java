package cn.tolim.pudans.contract.core.extension.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        group = AiRouteGroup.GROUP,
        version = AiRouteGroup.VERSION,
        kind = AiRouteGroup.KIND,
        plural = "airoutegroups",
        singular = "airoutegroup")
public class AiRouteGroup extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiRouteGroup";

    private Spec spec = new Spec();

    public enum RouteMode {
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
        EXPIRING,
        MANUAL
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        private RouteMode routeMode = RouteMode.SMART;

        private AiModel.Tier defaultTier;

        private Double qualityThreshold = 0.6;

        private AiProvider.Affinity affinity = AiProvider.Affinity.AUTO;

        private List<String> fallback = new ArrayList<>();

        private Integer patienceSeconds = 60;

        private Integer firstTokenSeconds = 0;

        private boolean sink;

        private String contextMode = "SMALLEST";

        private String manualPick;

        private List<Rule> rules = new ArrayList<>();

        @JsonProperty("isDefault")
        private boolean isDefault;

        private boolean enabled = true;

        private List<Member> members = new ArrayList<>();
    }

    @Data
    public static class Member {

        @Schema(requiredMode = REQUIRED)
        private String model;

        private AiModel.Tier tierOverride;

        private int weight = 1;

        private List<String> capabilityTags = new ArrayList<>();

        private int extraContextMargin = 1024;

        private boolean enabled = true;
    }

    @Data
    public static class Rule {

        private String name;

        private String condition;

        private String member;

        private int priority;
    }
}
