package cn.tolim.pudans.core.rbac.reconciler;

import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.BuiltinEntityDefinitions;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserGroupReconciler implements Reconciler<Request> {

    private final ExtensionClient client;

    public UserGroupReconciler(ExtensionClient client) {
        this.client = client;
    }

    @Override
    public Result reconcile(Request request) {
        client.fetch(UserGroup.class, request.name()).ifPresent(this::restore);
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new UserGroup()).build();
    }

    private void restore(UserGroup group) {
        BuiltinEntityDefinitions.group(group.getMetadata().getName()).ifPresent(canonical -> {
            var changed = false;
            if (group.getMetadata().getDeletionTimestamp() != null) {
                group.getMetadata().setDeletionTimestamp(null);
                changed = true;
            }
            if (!Objects.equals(group.getRoleRefs(), canonical.getRoleRefs())) {
                group.setRoleRefs(canonical.getRoleRefs());
                changed = true;
            }
            if (!Objects.equals(group.getDisplayName(), canonical.getDisplayName())) {
                group.setDisplayName(canonical.getDisplayName());
                changed = true;
            }
            if (!Objects.equals(group.getDescription(), canonical.getDescription())) {
                group.setDescription(canonical.getDescription());
                changed = true;
            }
            var labels = MetadataUtil.nullSafeLabels(group);
            if (!Boolean.parseBoolean(labels.get(BuiltinEntities.BUILTIN_LABEL))) {
                labels.put(BuiltinEntities.BUILTIN_LABEL, "true");
                changed = true;
            }
            if (!changed) {
                return;
            }
            log.warn(
                    "内置用户组 [{}] 已被修改或删除，正在恢复内置定义",
                    group.getMetadata().getName());
            client.update(group);
        });
    }
}
