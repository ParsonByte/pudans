package cn.tolim.pudans.contract.setting;

import static cn.tolim.pudans.contract.extension.GroupVersionKind.fromExtension;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import cn.tolim.pudans.contract.extension.GroupVersionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "", version = "v1alpha1", kind = Setting.KIND, plural = "settings", singular = "setting")
public class Setting extends AbstractExtension {

    public static final String KIND = "Setting";

    public static final GroupVersionKind GVK = fromExtension(Setting.class);


    @Schema(requiredMode = REQUIRED)
    private SettingSpec spec;


    @Data
    public static class SettingSpec {


        @Schema(requiredMode = REQUIRED, minLength = 1)
        private List<SettingForm> forms;
    }


    @Data
    public static class SettingForm {


        @Schema(requiredMode = REQUIRED)
        private String group;


        private String label;


        @Schema(requiredMode = REQUIRED)
        private List<Object> formSchema;
    }
}
