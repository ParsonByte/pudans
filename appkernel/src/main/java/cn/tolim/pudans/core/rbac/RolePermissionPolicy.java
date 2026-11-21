package cn.tolim.pudans.core.rbac;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.Role.PolicyRule;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.core.rbac.permission.PermissionDirectory;
import cn.tolim.pudans.core.rbac.permission.UiPermissions;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
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

    // 校验新建角色不超出调用方现有权限范围。
    public Mono<Void> checkCreate(Role requested) {
        return actorContext()
                .flatMap(actor -> validateAggregateLabels(actor, requested)
                        .then(checkRequestedPermissions(actor, requested, null)));
    }

    // 更新时合并角色原有权限，避免误判为越权。
    public Mono<Void> checkUpdate(Role requested) {
        return actorContext().flatMap(actor -> {
            var name = requested.getMetadata() == null ? null : requested.getMetadata().getName();
            return validateAggregateLabels(actor, requested)
                    .then(checkRequestedPermissions(actor, requested, name));
        });
    }

    // 授予前确认目标角色权限不超过调用方权限。
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

    // 对比请求角色的有效权限与调用方可授予权限。
    private Mono<Void> checkRequestedPermissions(ActorContext actor, Role requested, String existingRoleName) {
        if (actor.superRole()) {
            return Mono.empty();
        }
        return Mono.zip(
                        requestedPermissions(requested),
                        grantedPermissions(actor.roleNames(), existingRoleName))
                .flatMap(permissions -> validate(permissions.getT1(), permissions.getT2()));
    }

    // 聚合标签会让持有者继承规则，仅超管可设置。
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

    // 解析角色聚合标签，得到被聚合的目标角色名。
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

    // 读取当前认证角色，未认证时拒绝。
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

    // 计算调用方权限，更新场景合并角色原有权限。
    private Mono<EffectivePermissions> grantedPermissions(Set<String> actorRoles, String existingRoleName) {
        var actorPermissions = effectivePermissions(actorRoles);
        if (StringUtils.isBlank(existingRoleName)) {
            return actorPermissions;
        }
        var existingPermissions = effectivePermissions(Set.of(existingRoleName));
        return Mono.zip(actorPermissions, existingPermissions)
                .map(permissions -> merge(permissions.getT1(), permissions.getT2()));
    }

    // 聚合指定角色的规则与界面权限并展开。
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

    // 汇总请求角色自身、依赖与聚合目标的权限。
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

    // 按聚合标签反查聚合到该角色的目标角色。
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

    // 解析角色依赖注解，格式错误时按无依赖处理。
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

    // 合并两套有效权限，作为更新时的权限上限。
    private static EffectivePermissions merge(EffectivePermissions left, EffectivePermissions right) {
        var uiPermissions = new LinkedHashSet<>(left.uiPermissions());
        uiPermissions.addAll(right.uiPermissions());
        var rules = new ArrayList<>(left.rules());
        rules.addAll(right.rules());
        return new EffectivePermissions(uiPermissions, rules);
    }

    // 界面权限与策略规则都必须被授权集合覆盖。
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

    // 把可空规则列表转为不可空列表。
    private static List<PolicyRule> nullSafe(List<PolicyRule> rules) {
        return rules == null ? List.of() : rules;
    }

    // 构造权限越界的统一拒绝异常。
    private static AccessDeniedException denied() {
        return denied("不能授予超出当前账号权限范围的角色权限。");
    }

    // 构造带具体原因的拒绝异常。
    private static AccessDeniedException denied(String message) {
        return new AccessDeniedException(message);
    }

    private record ActorContext(Set<String> roleNames, boolean superRole) {}

    private record EffectivePermissions(Set<String> uiPermissions, List<PolicyRule> rules) {

        // 空权限集合，用于无角色或占位场景。
        static EffectivePermissions empty() {
            return new EffectivePermissions(Set.of(), List.of());
        }
    }
}
