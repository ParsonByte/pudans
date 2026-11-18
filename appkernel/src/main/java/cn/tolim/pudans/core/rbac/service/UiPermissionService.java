package cn.tolim.pudans.core.rbac.service;

import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UiPermissionService {

    public static final String ALL_UI_PERMISSIONS = "*";

    private final UserGroupService userGroupService;

    private final RoleService roleService;

    private final PermissionCatalogService permissionCatalogService;

    public Mono<List<String>> uiPermissionsOf(String username) {
        return userGroupService
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
                .flatMap(permissions -> permissionCatalogService.hasCapability(permissions, capability));
    }

    // 控制台访问规则：超级角色始终可访问，否则所有已分配角色都必须带有禁用注解。
    public Mono<Boolean> isConsoleAccessDisallowed(String username) {
        return userGroupService
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
                    set.addAll(uiPermissionsOf(role));
                    return set;
                })
                .map(merged -> normalize(List.copyOf(merged)))
                .flatMap(permissionCatalogService::expand)
                .map(UiPermissionService::normalize)
                .defaultIfEmpty(List.of());
    }

    private List<String> uiPermissionsOf(Role role) {
        var annotations = role.getMetadata() == null ? null : role.getMetadata().getAnnotations();
        if (annotations == null) {
            return List.of();
        }
        var raw = annotations.get(Role.UI_PERMISSIONS_ANNO);
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        try {
            List<String> permissions = JsonUtils.jsonToObject(raw, new TypeReference<>() {});
            return permissions == null ? List.of() : permissions.stream().filter(StringUtils::isNotBlank).toList();
        } catch (Exception e) {
            log.warn("无法解析角色 [{}] 的 UI 权限注解", role.getMetadata().getName(), e);
            return List.of();
        }
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
