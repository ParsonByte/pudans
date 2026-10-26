package cn.tolim.pudans.plugin.extensionpoint;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "plugin.pudans.tolim.cn",
        version = "v1alpha1",
        kind = "ExtensionPointDefinition",
        singular = "extensionpointdefinition",
        plural = "extensionpointdefinitions")
public class ExtensionPointDefinition extends AbstractExtension {


    @Schema(requiredMode = REQUIRED)
    private ExtensionPointSpec spec;


    public enum ExtensionPointType {
        SINGLETON,
        MULTI_INSTANCE;
    }

    @Data
    public static class ExtensionPointSpec {

        @Schema(requiredMode = REQUIRED)
        private String className;


        @Schema(requiredMode = REQUIRED)
        private String displayName;


        @Schema(requiredMode = REQUIRED)
        private ExtensionPointType type;


        private String description;


        private String icon;
    }
}
