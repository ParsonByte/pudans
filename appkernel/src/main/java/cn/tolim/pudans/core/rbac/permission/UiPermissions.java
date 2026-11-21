package cn.tolim.pudans.core.rbac.permission;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.RoleUiPermissions;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UiPermissions {

    public static final String ALL_UI_PERMISSIONS = "*";

    private final UserRoles userRoles;

    private final Roles roleService;

    private final PermissionDirectory permissionDirectory;

    // 汇总用户全部角色的界面权限并展开。
    public Mono<List<String>> uiPermissionsOf(String username) {
        return userRoles
                .list(username)
                .collectList()
                .flatMap(roleNames -> {
                    if (AuthorityUtils.containsSuperRole(Set.copyOf(roleNames))) {
                        return Mono.just(List.of(ALL_UI_PERMISSIONS));
                    }
                    return aggregate(Set.copyOf(roleNames));
                });
    }

    // 判断用户是否具备指定界面能力。
    public Mono<Boolean> hasCapability(String username, String capability) {
        return uiPermissionsOf(username)
                .flatMap(permissions -> permissionDirectory.hasCapability(permissions, capability));
    }

    // 控制台访问规则：超级角色始终可访问，否则所有已分配角色都必须带有禁用注解。
    public Mono<Boolean> isConsoleAccessDisallowed(String username) {
        return userRoles
                .list(username)
                .collectList()
                .flatMap(roleNames -> {
                    if (roleNames.isEmpty()
                            || AuthorityUtils.containsSuperRole(Set.copyOf(roleNames))) {
                        return Mono.just(false);
                    }
                    return roleService
                            .list(Set.copyOf(roleNames))
                            .collectList()
                            .map(roles -> !roles.isEmpty()
                                    && roles.stream().allMatch(role -> {
                                        var annotations = role.getMetadata() == null
                                                ? null
                                                : role.getMetadata().getAnnotations();
                                        return annotations != null
                                                && "true"
                                                        .equals(annotations.get(
                                                                Role.DISALLOW_ACCESS_CONSOLE_ANNO));
                                    }));
                });
    }

    // 合并角色权限并展开后返回规范化结果。
    private Mono<List<String>> aggregate(Set<String> roleNames) {
        return roleService
                .listPermissions(roleNames)
                .reduce(new LinkedHashSet<String>(), (set, role) -> {
                    set.addAll(RoleUiPermissions.of(role));
                    return set;
                })
                .map(merged -> normalize(List.copyOf(merged)))
                .flatMap(permissionDirectory::expand)
                .map(UiPermissions::normalize)
                .defaultIfEmpty(List.of());
    }

    // 去空去重，全量权限收敛为通配符。
    static List<String> normalize(List<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        var distinct = new LinkedHashSet<String>();
        permissions.stream().filter(StringUtils::isNotBlank).forEach(distinct::add);
        if (distinct.contains(ALL_UI_PERMISSIONS)) {
            return List.of(ALL_UI_PERMISSIONS);
        }
        return new ArrayList<>(distinct);
    }
}
