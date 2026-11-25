package cn.tolim.pudans.contract.storage;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.contract.AbstractExtension;
import cn.tolim.pudans.extension.contract.GVK;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = StorageRoute.GROUP,
        version = StorageRoute.VERSION,
        kind = StorageRoute.KIND,
        plural = "storageroutes",
        singular = "storageroute")
public class StorageRoute extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "StorageRoute";

    private Spec spec = new Spec();

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private Purpose purpose;

        @Schema(requiredMode = REQUIRED)
        private String policyName;

        private boolean enabled = true;

        private String description;
    }

    public enum Purpose {
        CREATION_RESULT("creation_result", "creation-result", true),
        SANDBOX_SNAPSHOT("sandbox_snapshot", "sandbox-snapshot", false);

        private final String code;

        private final String routeName;

        private final boolean fallbackToDefaultPolicy;

        Purpose(String code, String routeName, boolean fallbackToDefaultPolicy) {
            this.code = code;
            this.routeName = routeName;
            this.fallbackToDefaultPolicy = fallbackToDefaultPolicy;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        public String routeName() {
            return routeName;
        }

        public boolean fallbackToDefaultPolicy() {
            return fallbackToDefaultPolicy;
        }

        @JsonCreator
        public static Purpose fromCode(String code) {
            for (var purpose : values()) {
                if (purpose.code.equalsIgnoreCase(code)) {
                    return purpose;
                }
            }
            throw new IllegalArgumentException("不支持的存储路由用途：" + code);
        }
    }
}
