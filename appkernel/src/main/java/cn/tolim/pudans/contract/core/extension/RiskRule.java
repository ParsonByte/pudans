package cn.tolim.pudans.contract.core.extension;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = RiskRule.GROUP,
        version = RiskRule.VERSION,
        kind = RiskRule.KIND,
        plural = "riskrules",
        singular = "riskrule")
public class RiskRule extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "RiskRule";

    public static final String ACTION_BLOCK = "BLOCK";
    public static final String ACTION_ALERT = "ALERT";

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec = new Spec();

    public static RiskRule of(RiskRule.Spec spec) {
        var rule = new RiskRule();
        rule.setSpec(spec);
        return rule;
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String scene;

        private String description;

        private boolean enabled = true;

        private long windowSeconds = 60L;

        private long threshold = 60L;

        private long blockSeconds = 60L;

        private String metric = "IP";

        private String action = ACTION_BLOCK;

        public boolean blocking() {
            return blockSeconds > 0 && !ACTION_ALERT.equals(action);
        }
    }
}
