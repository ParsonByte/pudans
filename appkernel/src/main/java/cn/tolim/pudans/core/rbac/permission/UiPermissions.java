package cn.tolim.pudans.core.rbac.permission;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.RoleUiPermissions;
import cn.tolim.pudans.core.security.authorization.AuthorityUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.rbac.group.Groups;

@Service
@RequiredArgsConstructor
public class UiPermissions {

    public static final String ALL_UI_PERMISSIONS = "*";

    private final Groups groups;

    private final Roles roleService;

    private final PermissionDirectory permissionDirectory;

    public Mono<List<String>> uiPermissionsOf(String username) {
        return groups
                .getRoleNamesByUsername(username)
                .collectList()
                .flatMap(roleNames -> {
                    if (AuthorityUtils.containsSuperRole(Set.copyOf(roleNames))) {
                        return Mono.just(List.of(ALL_UI_PERMISSIONS));
                    }
                    return aggregate(Set.copyOf(roleNames));
                });
    }

    public Mono<Boolean> hasCapability(String username, String capability) {
        return uiPermissionsOf(username)
                .flatMap(permissions -> permissionDirectory.hasCapability(permissions, capability));
    }

    // 控制台访问规则：超级角色始终可访问，否则所有已分配角色都必须带有禁用注解。
    public Mono<Boolean> isConsoleAccessDisallowed(String username) {
        return groups
                .getRoleNamesByUsername(username)
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
