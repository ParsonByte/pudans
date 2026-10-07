package cn.tolim.pudans.core.rbac.reconciler;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
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
            reconcileSuperAdminBinding(binding);
            return Result.doNotRetry();
        }

        if (binding.getRoleRef() != null && hasSuperAdminSubject(binding)) {
            log.warn(
                    "Binding [{}] grants super administrator membership outside of the canonical binding, removing it",
                    bindingName);
            deleteBinding(binding);
        }
        return Result.doNotRetry();
    }

    private void reconcileSuperAdminBinding(UserGroupBinding binding) {
        var owner = superAdminResolver.ownerOf(binding);
        if (owner == null || client.fetch(User.class, owner).isEmpty()) {
            log.warn(
                    "Super administrator binding [{}] does not reference an existing user, removing it",
                    binding.getMetadata().getName());
            deleteBinding(binding);
            return;
        }
        if (superAdminResolver.hasOtherCanonicalSuperAdmin(owner)) {
            log.warn(
                    "Binding [{}] is a forged duplicate of an existing super administrator binding, removing it",
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
        var roleRef = binding.getRoleRef();
        if (roleRef == null
                || !UserGroup.KIND.equals(roleRef.getKind())
                || !BuiltinEntities.SUPER_ADMINS_GROUP.equals(roleRef.getName())) {
            binding.setRoleRef(UserGroupBinding.RoleRef.userGroup(BuiltinEntities.SUPER_ADMINS_GROUP));
            changed = true;
        }
        if (ExtensionUtil.addFinalizers(binding.getMetadata(), Set.of(MetadataUtil.SYSTEM_FINALIZER))) {
            changed = true;
        }
        if (!changed) {
            return;
        }
        log.warn(
                "Membership binding [{}] of the super administrator has been tampered with, restoring it in place",
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

    private boolean hasSuperAdminSubject(UserGroupBinding binding) {
        if (CollectionUtils.isEmpty(binding.getSubjects())) {
            return false;
        }
        return binding.getSubjects().stream()
                .filter(subject -> User.KIND.equals(subject.getKind()))
                .anyMatch(subject -> superAdminResolver.hasSuperAdminBindingBlocking(subject.getName()));
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
