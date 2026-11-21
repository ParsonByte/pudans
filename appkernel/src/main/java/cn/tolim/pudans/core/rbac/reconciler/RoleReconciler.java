package cn.tolim.pudans.core.rbac.reconciler;

import static java.util.Objects.deepEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.Role.PolicyRule;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;

@Slf4j
@Component
public class RoleReconciler implements Reconciler<Request> {

    private final ExtensionClient client;

    private final BuiltinRoleDefinitions builtinRoleDefinitions;

    // 注入扩展客户端与内置定义，用于恢复校验。
    public RoleReconciler(ExtensionClient client, BuiltinRoleDefinitions builtinRoleDefinitions) {
        this.client = client;
        this.builtinRoleDefinitions = builtinRoleDefinitions;
    }

    // 恢复被篡改的内置角色，否则同步依赖注解。
    @Override
    public Result reconcile(Request request) {
        client.fetch(Role.class, request.name()).ifPresent(role -> {
            if (restore(role)) {
                return;
            }
            Map<String, String> annotations = MetadataUtil.nullSafeAnnotations(role);
            annotations.put(Role.ROLE_DEPENDENCY_RULES, "[]");
            updateLabelsAndAnnotations(role);
        });
        return Result.doNotRetry();
    }

    // 声明该协调器负责角色资源。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Role()).build();
    }

    // 恢复内置角色定义，返回是否发生修复。
    private boolean restore(Role role) {
        return builtinRoleDefinitions.role(role.getMetadata().getName())
                .map(canonical -> {
                    var changed = false;
                    if (role.getMetadata().getDeletionTimestamp() != null) {
                        role.getMetadata().setDeletionTimestamp(null);
                        changed = true;
                    }
                    if (!sameRules(role.getRules(), canonical.getRules())) {
                        role.setRules(canonical.getRules());
                        changed = true;
                    }
                    var labels = MetadataUtil.nullSafeLabels(role);
                    if (!Boolean.parseBoolean(labels.get(BuiltinEntities.BUILTIN_LABEL))) {
                        labels.put(BuiltinEntities.BUILTIN_LABEL, "true");
                        changed = true;
                    }
                    var annotations = MetadataUtil.nullSafeAnnotations(role);
                    var canonicalUiPermissions =
                            MetadataUtil.nullSafeAnnotations(canonical).get(Role.UI_PERMISSIONS_ANNO);
                    if (!Objects.equals(
                            canonicalUiPermissions, annotations.get(Role.UI_PERMISSIONS_ANNO))) {
                        if (canonicalUiPermissions == null) {
                            annotations.remove(Role.UI_PERMISSIONS_ANNO);
                        } else {
                            annotations.put(Role.UI_PERMISSIONS_ANNO, canonicalUiPermissions);
                        }
                        changed = true;
                    }
                    var canonicalDependencies =
                            MetadataUtil.nullSafeAnnotations(canonical).get(Role.ROLE_DEPENDENCIES_ANNO);
                    if (!Objects.equals(canonicalDependencies, annotations.get(Role.ROLE_DEPENDENCIES_ANNO))) {
                        if (canonicalDependencies == null) {
                            annotations.remove(Role.ROLE_DEPENDENCIES_ANNO);
                        } else {
                            annotations.put(Role.ROLE_DEPENDENCIES_ANNO, canonicalDependencies);
                        }
                        changed = true;
                    }
                    if (!changed) {
                        return false;
                    }
                    log.warn(
                            "内置角色 [{}] 已被修改或删除，正在恢复内置定义",
                            role.getMetadata().getName());
                    client.update(role);
                    return true;
                })
                .orElse(false);
    }

    // 按顺序逐条比较规则内容是否一致。
    private static boolean sameRules(List<PolicyRule> left, List<PolicyRule> right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (var i = 0; i < left.size(); i++) {
            var expected = left.get(i);
            var actual = right.get(i);
            if (!sameValues(expected.getApiGroups(), actual.getApiGroups())
                    || !sameValues(expected.getResources(), actual.getResources())
                    || !sameValues(expected.getResourceNames(), actual.getResourceNames())
                    || !sameValues(expected.getNonResourceURLs(), actual.getNonResourceURLs())
                    || !sameValues(expected.getVerbs(), actual.getVerbs())) {
                return false;
            }
        }
        return true;
    }

    // 比较数组内容，空数组与空值等价。
    private static boolean sameValues(String[] left, String[] right) {
        return Arrays.equals(left == null ? new String[0] : left, right == null ? new String[0] : right);
    }

    // 在最新对象上回写标签与注解，避免并发覆盖。
    private void updateLabelsAndAnnotations(Role role) {
        var annotations = role.getMetadata().getAnnotations();
        var labels = role.getMetadata().getLabels();
        client.fetch(Role.class, role.getMetadata().getName())
                .filter(freshRole ->
                        !deepEquals(annotations, freshRole.getMetadata().getAnnotations())
                                || !deepEquals(labels, freshRole.getMetadata().getLabels()))
                .ifPresent(freshRole -> {
                    freshRole.getMetadata().setAnnotations(annotations);
                    freshRole.getMetadata().setLabels(labels);
                    client.update(freshRole);
                });
    }
}
