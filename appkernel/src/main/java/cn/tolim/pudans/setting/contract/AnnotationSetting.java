package cn.tolim.pudans.setting.contract;

import static cn.tolim.pudans.setting.contract.AnnotationSetting.KIND;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.contract.AbstractExtension;
import cn.tolim.pudans.extension.contract.GVK;
import cn.tolim.pudans.extension.contract.GroupKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(group = "", version = "v1alpha1", kind = KIND, plural = "annotationsettings", singular = "annotationsetting")
public class AnnotationSetting extends AbstractExtension {
    public static final String TARGET_REF_LABEL = "pudans.tolim.cn/target-ref";

    public static final String KIND = "AnnotationSetting";


    @Schema(requiredMode = REQUIRED)
    private AnnotationSettingSpec spec;


    @Data
    public static class AnnotationSettingSpec {

        @Schema(requiredMode = REQUIRED)
        private GroupKind targetRef;


        @Schema(requiredMode = REQUIRED, minLength = 1)
        private List<Object> formSchema;
    }
}
