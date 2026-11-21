package cn.tolim.pudans.contract.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
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
    public static final String BUILTIN_FAST_GROUP = "fast-group";
    public static final String BUILTIN_PRO_GROUP = "pro-group";

    private Spec spec = new Spec();

    // 内置固定组的父模式由名称强制决定，配置值不得覆盖。
    public ParentMode effectiveParentMode() {
        var name = getMetadata() == null ? null : getMetadata().getName();
        if (BUILTIN_FAST_GROUP.equals(name)) {
            return ParentMode.FAST;
        }
        if (BUILTIN_PRO_GROUP.equals(name)) {
            return ParentMode.PRO;
        }
        var configured = spec == null ? null : spec.getParentMode();
        return configured == null ? ParentMode.NONE : configured;
    }

    public enum ParentMode {
        FAST,
        PRO,
        NONE
    }

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

        private ParentMode parentMode = ParentMode.NONE;

        private Double qualityThreshold = 0.6;

        private AiProvider.Affinity affinity = AiProvider.Affinity.AUTO;

        private List<String> fallback = new ArrayList<>();

        private List<String> accounts = new ArrayList<>();

        private int order;

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

        private List<String> agents = new ArrayList<>();

        private Integer tokensGte;

        private Integer imagesGte;

        private boolean pause;

        private String use;
    }
}
