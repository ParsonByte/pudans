package cn.tolim.pudans.infra.setting.reconciler;

import cn.tolim.pudans.setting.api.AnnotationSetting;
import cn.tolim.pudans.extension.api.ExtensionClient;
import cn.tolim.pudans.extension.api.GroupKind;
import cn.tolim.pudans.extension.api.MetadataUtil;
import cn.tolim.pudans.extension.api.controller.Controller;
import cn.tolim.pudans.extension.api.controller.ControllerBuilder;
import cn.tolim.pudans.extension.api.controller.Reconciler;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@AllArgsConstructor
public class AnnotationSettingReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        populateDefaultLabels(request.name());
        return new Result(false, null);
    }

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

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AnnotationSetting()).build();
    }
}
