package cn.tolim.pudans.core.rbac.reconciler;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class UserGroupReconciler implements Reconciler<Request> {

    private final ExtensionClient client;

    private final BuiltinRoleDefinitions builtinRoleDefinitions;

    public UserGroupReconciler(ExtensionClient client, BuiltinRoleDefinitions builtinRoleDefinitions) {
        this.client = client;
        this.builtinRoleDefinitions = builtinRoleDefinitions;
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

    private List<UserGroup.RoleRef> expectedRoleRefs(UserGroup canonical, UserGroup current) {
        var canonicalRefs = canonical.getRoleRefs() == null
                ? List.<UserGroup.RoleRef>of()
                : canonical.getRoleRefs();
        var expected = new ArrayList<>(canonicalRefs);
        if (BuiltinEntities.SUPER_ADMINS_GROUP.equals(canonical.getMetadata().getName())) {
            return expected;
        }
        var seenNames = new LinkedHashSet<String>();
        for (var roleRef : canonicalRefs) {
            if (roleRef != null && StringUtils.hasText(roleRef.getName())) {
                seenNames.add(roleRef.getName());
            }
        }
        var currentRefs = current.getRoleRefs() == null
                ? List.<UserGroup.RoleRef>of()
                : current.getRoleRefs();
        for (var roleRef : currentRefs) {
            if (roleRef == null
                    || !Role.KIND.equals(roleRef.getKind())
                    || !StringUtils.hasText(roleRef.getName())
                    || seenNames.contains(roleRef.getName())
                    || builtinRoleDefinitions.role(roleRef.getName()).isPresent()) {
                continue;
            }
            expected.add(roleRef);
            seenNames.add(roleRef.getName());
        }
        return expected;
    }

    private void restore(UserGroup group) {
        builtinRoleDefinitions.group(group.getMetadata().getName()).ifPresent(canonical -> {
            var changed = false;
            if (group.getMetadata().getDeletionTimestamp() != null) {
                group.getMetadata().setDeletionTimestamp(null);
                changed = true;
            }
            var expectedRoleRefs = expectedRoleRefs(canonical, group);
            if (!Objects.equals(group.getRoleRefs(), expectedRoleRefs)) {
                group.setRoleRefs(expectedRoleRefs);
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
