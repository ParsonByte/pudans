package cn.tolim.pudans.core.rbac.reconciler;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.SuperAdminResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserGroupBindingReconciler implements Reconciler<Request> {

    private final ExtensionClient client;

    private final SuperAdminResolver superAdminResolver;

    @Override
    public Result reconcile(Request request) {
        var binding = client.fetch(UserGroupBinding.class, request.name()).orElse(null);
        if (binding == null) {
            return Result.doNotRetry();
        }

        var bindingName = binding.getMetadata() == null ? null : binding.getMetadata().getName();
        if (superAdminResolver.isSuperAdminBindingName(bindingName)) {
            // 名字与 roleRef 不一致即为伪造绑定，只能删除，修复等于把伪造对象升级为超管成员
            if (!isSuperAdminsBinding(binding)) {
                log.warn("超管绑定名 [{}] 未指向 super-admins 用户组，正在移除", bindingName);
                deleteBinding(binding);
                return Result.doNotRetry();
            }
            reconcileSuperAdminBinding(binding);
            return Result.doNotRetry();
        }

        if (binding.getRoleRef() != null && hasSuperAdminSubject(binding)) {
            log.warn(
                    "绑定 [{}] 在规范绑定之外授予了超级管理员成员资格，正在移除",
                    bindingName);
            deleteBinding(binding);
        }
        return Result.doNotRetry();
    }

    private void reconcileSuperAdminBinding(UserGroupBinding binding) {
        var owner = superAdminResolver.ownerOf(binding);
        if (owner == null || client.fetch(User.class, owner).isEmpty()) {
            log.warn(
                    "超级管理员绑定 [{}] 未引用现有用户，正在移除",
                    binding.getMetadata().getName());
            deleteBinding(binding);
            return;
        }
        if (superAdminResolver.hasOtherCanonicalSuperAdmin(owner)) {
            log.warn(
                    "绑定 [{}] 是现有超级管理员绑定的伪造副本，正在移除",
                    binding.getMetadata().getName());
            deleteBinding(binding);
            return;
        }
        restore(binding, owner);
    }

    private void restore(UserGroupBinding binding, String owner) {
        var changed = false;
        if (binding.getMetadata().getDeletionTimestamp() != null) {
            binding.getMetadata().setDeletionTimestamp(null);
            changed = true;
        }
        if (!hasSingleSubject(binding, owner)) {
            binding.setSubjects(new LinkedList<>(List.of(UserGroupBinding.Subject.user(owner))));
            changed = true;
        }
        if (ExtensionUtil.addFinalizers(binding.getMetadata(), Set.of(MetadataUtil.SYSTEM_FINALIZER))) {
            changed = true;
        }
        if (!changed) {
            return;
        }
        log.warn(
                "超级管理员的成员绑定 [{}] 已被篡改，正在原地恢复",
                binding.getMetadata().getName());
        client.update(binding);
    }

    private boolean hasSingleSubject(UserGroupBinding binding, String owner) {
        var subjects = binding.getSubjects();
        return !CollectionUtils.isEmpty(subjects)
                && subjects.size() == 1
                && User.KIND.equals(subjects.get(0).getKind())
                && owner.equals(subjects.get(0).getName());
    }

    private boolean isSuperAdminsBinding(UserGroupBinding binding) {
        var roleRef = binding.getRoleRef();
        return roleRef != null
                && UserGroup.KIND.equals(roleRef.getKind())
                && BuiltinEntities.SUPER_ADMINS_GROUP.equals(roleRef.getName());
    }

    private boolean hasSuperAdminSubject(UserGroupBinding binding) {
        if (CollectionUtils.isEmpty(binding.getSubjects())) {
            return false;
        }
        return binding.getSubjects().stream()
                .filter(subject -> User.KIND.equals(subject.getKind()))
                .anyMatch(subject -> superAdminResolver.hasCanonicalBindingBlocking(subject.getName()));
    }

    private void deleteBinding(UserGroupBinding binding) {
        if (binding.getMetadata().getDeletionTimestamp() == null) {
            binding.getMetadata().setDeletionTimestamp(Instant.now());
        }
        ExtensionUtil.removeFinalizers(binding.getMetadata(), Set.of(MetadataUtil.SYSTEM_FINALIZER));
        client.update(binding);
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new UserGroupBinding()).build();
    }
}
