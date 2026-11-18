package cn.tolim.pudans.core.rbac;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.Role.PolicyRule;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.rbac.service.PermissionCatalogService;
import cn.tolim.pudans.core.rbac.service.UiPermissionService;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RolePermissionPolicy {

    private final RoleService roleService;

    private final ReactiveExtensionClient client;

    private final PermissionCatalogService permissionCatalogService;

    public Mono<Void> checkCreate(Role requested) {
        return actorContext().flatMap(actor -> {
            if (actor.superRole()) {
                return Mono.empty();
            }
            return Mono.zip(
                            requestedPermissions(requested),
                            grantedPermissions(actor.roleNames(), null))
                    .flatMap(permissions -> validate(permissions.getT1(), permissions.getT2()));
        });
    }

    public Mono<Void> checkUpdate(Role requested) {
        return actorContext().flatMap(actor -> {
            if (actor.superRole()) {
                return Mono.empty();
            }
            var name = requested.getMetadata() == null ? null : requested.getMetadata().getName();
            return Mono.zip(
                            requestedPermissions(requested),
                            grantedPermissions(actor.roleNames(), name))
                    .flatMap(permissions -> validate(permissions.getT1(), permissions.getT2()));
        });
    }

    private Mono<ActorContext> actorContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .map(authentication -> {
                    var roleNames = AuthorityUtils.authoritiesToRoles(authentication.getAuthorities());
                    return new ActorContext(roleNames, AuthorityUtils.containsSuperRole(roleNames));
                })
                .switchIfEmpty(Mono.error(denied()));
    }

    private Mono<EffectivePermissions> grantedPermissions(Set<String> actorRoles, String existingRoleName) {
        var actorPermissions = effectivePermissions(actorRoles);
        if (StringUtils.isBlank(existingRoleName)) {
            return actorPermissions;
        }
        var existingPermissions = effectivePermissions(Set.of(existingRoleName));
        return Mono.zip(actorPermissions, existingPermissions)
                .map(permissions -> merge(permissions.getT1(), permissions.getT2()));
    }

    private Mono<EffectivePermissions> effectivePermissions(Set<String> roleNames) {
        if (roleNames.isEmpty()) {
            return Mono.just(EffectivePermissions.empty());
        }
        var rules = roleService
                .listDependenciesFlux(roleNames)
                .flatMapIterable(role -> nullSafe(role.getRules()))
                .collectList();
        var uiPermissions = roleService
                .listPermissions(roleNames)
                .flatMapIterable(RoleUiPermissions::of)
                .collectList()
                .flatMap(permissionCatalogService::expand)
                .map(LinkedHashSet::new);
        return Mono.zip(rules, uiPermissions)
                .map(permissions -> new EffectivePermissions(permissions.getT2(), permissions.getT1()));
    }

    private Mono<EffectivePermissions> requestedPermissions(Role requested) {
        var name = requested.getMetadata() == null ? null : requested.getMetadata().getName();
        return aggregatedRoleNames(name).flatMap(aggregatedNames -> {
            var dependencyNames = new LinkedHashSet<>(dependencies(requested));
            dependencyNames.addAll(aggregatedNames);
            var dependencyRules = dependencyNames.isEmpty()
                    ? Mono.just(List.<PolicyRule>of())
                    : roleService
                            .listDependenciesFlux(dependencyNames)
                            .flatMapIterable(role -> nullSafe(role.getRules()))
                            .collectList();
            var dependencyUiPermissions = dependencyNames.isEmpty()
                    ? Mono.just(List.<String>of())
                    : roleService
                            .listPermissions(dependencyNames)
                            .flatMapIterable(RoleUiPermissions::of)
                            .collectList();
            return Mono.zip(dependencyRules, dependencyUiPermissions)
                    .flatMap(dependencies -> {
                        var rules = new ArrayList<>(nullSafe(requested.getRules()));
                        rules.addAll(dependencies.getT1());
                        var uiPermissions = new LinkedHashSet<>(RoleUiPermissions.of(requested));
                        uiPermissions.addAll(dependencies.getT2());
                        return permissionCatalogService
                                .expand(List.copyOf(uiPermissions))
                                .map(expanded ->
                                        new EffectivePermissions(new LinkedHashSet<>(expanded), rules));
                    });
        });
    }

    private Mono<Set<String>> aggregatedRoleNames(String roleName) {
        if (StringUtils.isBlank(roleName)) {
            return Mono.just(Set.of());
        }
        var options = ListOptions.builder()
                .labelSelector()
                .eq(Role.ROLE_AGGREGATE_LABEL_PREFIX + roleName, Boolean.TRUE.toString())
                .end()
                .build();
        return client.listAll(Role.class, options, defaultSort())
                .map(role -> role.getMetadata().getName())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private static Set<String> dependencies(Role role) {
        var raw = MetadataUtil.nullSafeAnnotations(role).get(Role.ROLE_DEPENDENCIES_ANNO);
        if (StringUtils.isBlank(raw)) {
            return Set.of();
        }
        try {
            List<String> dependencies = JsonUtils.jsonToObject(raw, new TypeReference<>() {});
            if (dependencies == null) {
                return Set.of();
            }
            return dependencies.stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static EffectivePermissions merge(EffectivePermissions left, EffectivePermissions right) {
        var uiPermissions = new LinkedHashSet<>(left.uiPermissions());
        uiPermissions.addAll(right.uiPermissions());
        var rules = new ArrayList<>(left.rules());
        rules.addAll(right.rules());
        return new EffectivePermissions(uiPermissions, rules);
    }

    private static Mono<Void> validate(EffectivePermissions requested, EffectivePermissions allowed) {
        var allUiPermissionsGranted =
                allowed.uiPermissions().contains(UiPermissionService.ALL_UI_PERMISSIONS);
        if (!allUiPermissionsGranted && !allowed.uiPermissions().containsAll(requested.uiPermissions())) {
            return Mono.error(denied());
        }
        if (!PolicyRuleCoverage.covers(allowed.rules(), requested.rules())) {
            return Mono.error(denied());
        }
        return Mono.empty();
    }

    private static List<PolicyRule> nullSafe(List<PolicyRule> rules) {
        return rules == null ? List.of() : rules;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("不能授予超出当前账号权限范围的角色权限。");
    }

    private record ActorContext(Set<String> roleNames, boolean superRole) {}

    private record EffectivePermissions(Set<String> uiPermissions, List<PolicyRule> rules) {

        static EffectivePermissions empty() {
            return new EffectivePermissions(Set.of(), List.of());
        }
    }
}
