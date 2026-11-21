package cn.tolim.pudans.core.rbac;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.Role.PolicyRule;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.rbac.permission.PermissionDirectory;
import cn.tolim.pudans.core.rbac.permission.UiPermissions;
import cn.tolim.pudans.core.infra.exception.AccessDeniedException;
import cn.tolim.pudans.core.security.authorization.AuthorityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RolePermissionPolicy {

    private final Roles roles;

    private final ReactiveExtensionClient client;

    private final PermissionDirectory permissionDirectory;

    public Mono<Void> checkCreate(Role requested) {
        return actorContext()
                .flatMap(actor -> validateAggregateLabels(actor, requested)
                        .then(checkRequestedPermissions(actor, requested, null)));
    }

    public Mono<Void> checkUpdate(Role requested) {
        return actorContext().flatMap(actor -> {
            var name = requested.getMetadata() == null ? null : requested.getMetadata().getName();
            return validateAggregateLabels(actor, requested)
                    .then(checkRequestedPermissions(actor, requested, name));
        });
    }

    public Mono<Void> checkGrant(Collection<String> roleNames) {
        var names = roleNames == null
                ? Set.<String>of()
                : roleNames.stream()
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return actorContext().flatMap(actor -> {
            if (actor.superRole()) {
                return Mono.empty();
            }
            return Mono.zip(
                            effectivePermissions(names),
                            grantedPermissions(actor.roleNames(), null))
                    .flatMap(permissions -> validate(permissions.getT1(), permissions.getT2()));
        });
    }

    private Mono<Void> checkRequestedPermissions(ActorContext actor, Role requested, String existingRoleName) {
        if (actor.superRole()) {
            return Mono.empty();
        }
        return Mono.zip(
                        requestedPermissions(requested),
                        grantedPermissions(actor.roleNames(), existingRoleName))
                .flatMap(permissions -> validate(permissions.getT1(), permissions.getT2()));
    }

    private Mono<Void> validateAggregateLabels(ActorContext actor, Role requested) {
        var targets = aggregateTargets(requested);
        if (targets.isEmpty()) {
            return Mono.empty();
        }
        // 聚合标签让目标角色的持有人继承本角色的全部规则，只允许超级管理员设置
        if (!actor.superRole()) {
            return Mono.error(denied("只有超级管理员可以设置角色聚合标签。"));
        }
        return Flux.fromIterable(targets)
                .concatMap(target -> client.fetch(Role.class, target)
                        .switchIfEmpty(Mono.error(
                                denied("角色聚合标签的目标角色 [" + target + "] 不存在。"))))
                .then();
    }

    private static Set<String> aggregateTargets(Role role) {
        if (role == null || role.getMetadata() == null) {
            return Set.of();
        }
        return MetadataUtil.nullSafeLabels(role).entrySet().stream()
                .filter(entry -> Boolean.parseBoolean(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(key -> key.startsWith(Role.ROLE_AGGREGATE_LABEL_PREFIX))
                .map(key -> key.substring(Role.ROLE_AGGREGATE_LABEL_PREFIX.length()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
        var rules = roles
                .listDependenciesFlux(roleNames)
                .flatMapIterable(role -> nullSafe(role.getRules()))
                .collectList();
        var uiPermissions = roles
                .listPermissions(roleNames)
                .flatMapIterable(RoleUiPermissions::of)
                .collectList()
                .flatMap(permissionDirectory::expand)
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
                    : roles
                            .listDependenciesFlux(dependencyNames)
                            .flatMapIterable(role -> nullSafe(role.getRules()))
                            .collectList();
            var dependencyUiPermissions = dependencyNames.isEmpty()
                    ? Mono.just(List.<String>of())
                    : roles
                            .listPermissions(dependencyNames)
                            .flatMapIterable(RoleUiPermissions::of)
                            .collectList();
            return Mono.zip(dependencyRules, dependencyUiPermissions)
                    .flatMap(dependencies -> {
                        var rules = new ArrayList<>(nullSafe(requested.getRules()));
                        rules.addAll(dependencies.getT1());
                        var uiPermissions = new LinkedHashSet<>(RoleUiPermissions.of(requested));
                        uiPermissions.addAll(dependencies.getT2());
                        return permissionDirectory
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
                allowed.uiPermissions().contains(UiPermissions.ALL_UI_PERMISSIONS);
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
        return denied("不能授予超出当前账号权限范围的角色权限。");
    }

    private static AccessDeniedException denied(String message) {
        return new AccessDeniedException(message);
    }

    private record ActorContext(Set<String> roleNames, boolean superRole) {}

    private record EffectivePermissions(Set<String> uiPermissions, List<PolicyRule> rules) {

        static EffectivePermissions empty() {
            return new EffectivePermissions(Set.of(), List.of());
        }
    }
}
