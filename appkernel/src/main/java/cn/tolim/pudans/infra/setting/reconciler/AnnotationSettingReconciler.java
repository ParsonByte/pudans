package cn.tolim.pudans.infra.setting.reconciler;

import cn.tolim.pudans.setting.contract.AnnotationSetting;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.GroupKind;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@AllArgsConstructor
public class AnnotationSettingReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    // 同步注解设置的默认标签，不触发额外协调。
    @Override
    public Result reconcile(Request request) {
        populateDefaultLabels(request.name());
        return new Result(false, null);
    }

    // 计算目标引用标签，仅在变化时更新资源。
    private void populateDefaultLabels(String name) {
        client.fetch(AnnotationSetting.class, name).ifPresent(annotationSetting -> {
            Map<String, String> labels = MetadataUtil.nullSafeLabels(annotationSetting);
            String oldTargetRef = labels.get(AnnotationSetting.TARGET_REF_LABEL);

            GroupKind targetRef = annotationSetting.getSpec().getTargetRef();
            String targetRefLabel = targetRef.group() + "/" + targetRef.kind();
            labels.put(AnnotationSetting.TARGET_REF_LABEL, targetRefLabel);

            if (!java.util.Objects.equals(oldTargetRef, targetRefLabel)) {
                client.update(annotationSetting);
            }
        });
    }

    // 声明协调器负责的注解设置资源。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AnnotationSetting()).build();
    }
}
