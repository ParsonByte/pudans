package cn.tolim.pudans.core.user;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.containsSuperRole;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.RoleBinding;
import cn.tolim.pudans.contract.rbac.RoleBinding.RoleRef;
import cn.tolim.pudans.contract.rbac.RoleBinding.Subject;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.index.query.Queries;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.security.authentication.SuperAdminInitializer;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class ExtensionRoles implements Roles {

    private final ReactiveExtensionClient client;

    private final cn.tolim.pudans.core.rbac.permission.RbacCache rbacCache;

    // 注入扩展客户端与角色缓存
    public ExtensionRoles(
            ReactiveExtensionClient client, cn.tolim.pudans.core.rbac.permission.RbacCache rbacCache) {
        this.client = client;
        this.rbacCache = rbacCache;
    }

    // 只接受角色类型的引用
    private static boolean isRoleKind(RoleRef roleRef) {
        return Role.GROUP.equals(roleRef.getApiGroup()) && Role.KIND.equals(roleRef.getKind());
    }

    // 将用户名转换为角色绑定主题
    private static Subject toUserSubject(String username) {
        var subject = new Subject();
        subject.setApiGroup(User.GROUP);
        subject.setKind(User.KIND);
        subject.setName(username);
        return subject;
    }

    // 按需构造排除隐藏角色的条件
    private static ListOptions shouldExcludeHidden(boolean excludeHidden) {
        if (!excludeHidden) {
            return null;
        }
        return ListOptions.builder()
                .labelSelector()
                .notEq(Role.HIDDEN_LABEL_NAME, Boolean.TRUE.toString())
                .end()
                .build();
    }

    // 提取绑定中的角色引用
    private Flux<RoleRef> listRoleRefs(Subject subject) {
        return listRoleBindings(subject).map(RoleBinding::getRoleRef);
    }

    // 列出主题关联且未删除的绑定
    @Override
    public Flux<RoleBinding> listRoleBindings(Subject subject) {
        var listOptions = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(Queries.in("subjects", subject.toString()))
                .build();
        return client.listAll(RoleBinding.class, listOptions, defaultSort());
    }

    // 返回用户直接绑定的角色名
    @Override
    public Flux<String> getRolesByUsername(String username) {
        return listRoleRefs(toUserSubject(username))
                .filter(ExtensionRoles::isRoleKind)
                .map(RoleRef::getName);
    }

    // 批量聚合并返回每个用户的角色集合
    @Override
    public Mono<Map<String, Collection<String>>> getRolesByUsernames(Collection<String> usernames) {
        if (CollectionUtils.isEmpty(usernames)) {
            return Mono.just(Map.of());
        }
        var subjects = usernames.stream()
                .map(ExtensionRoles::toUserSubject)
                .map(Object::toString)
                .collect(Collectors.toSet());
        var listOptions = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(Queries.in("subjects", subjects))
                .build();

        return client.listAll(RoleBinding.class, listOptions, defaultSort())
                .collect(HashMap::new, (map, roleBinding) -> {
                    for (Subject subject : roleBinding.getSubjects()) {
                        if (subjects.contains(subject.toString())) {
                            var username = subject.getName();
                            var roleRef = roleBinding.getRoleRef();
                            if (isRoleKind(roleRef)) {
                                var roleName = roleRef.getName();
                                map.computeIfAbsent(username, k -> new HashSet<>())
                                        .add(roleName);
                            }
                        }
                    }
                });
    }

    // 超级管理员直接通过，否则展开依赖比较
    @Override
    public Mono<Boolean> contains(Collection<String> source, Collection<String> candidates) {
        if (source.contains(SuperAdminInitializer.SUPER_ROLE_NAME)) {
            return Mono.just(true);
        }
        return rbacCache
                .cachedRoleDependencies(
                        new HashSet<>(source),
                        listWithDependencies(new HashSet<>(source), shouldExcludeHidden(false))
                                .collectList())
                .map(roles -> roles.stream()
                        .map(role -> role.getMetadata().getName())
                        .collect(Collectors.toSet()))
                .map(roleNames -> roleNames.containsAll(candidates));
    }

    // 展开角色依赖并返回对应权限
    @Override
    public Flux<Role> listPermissions(Set<String> names) {
        if (containsSuperRole(names)) {

            return client.listAll(Role.class, shouldExcludeHidden(true), ExtensionUtil.defaultSort());
        }
        return listWithDependencies(names, shouldExcludeHidden(true));
    }

    // 通过缓存展开角色依赖
    @Override
    public Flux<Role> listDependenciesFlux(Set<String> names) {
        return rbacCache
                .cachedRoleDependencies(
                        names, listWithDependencies(names, shouldExcludeHidden(false)).collectList())
                .flatMapMany(Flux::fromIterable);
    }

    // 按名称批量查询未删除角色
    private Flux<Role> listRoles(Set<String> names, ListOptions additionalListOptions) {
        if (CollectionUtils.isEmpty(names)) {
            return Flux.empty();
        }

        var listOptions = Optional.ofNullable(additionalListOptions)
                .map(ListOptions::builder)
                .orElseGet(ListOptions::builder)
                .andQuery(notDeleting())
                .andQuery(Queries.in("metadata.name", names))
                .build();

        return client.listAll(Role.class, listOptions, ExtensionUtil.defaultSort());
    }

    // 递归展开依赖并补充聚合角色
    private Flux<Role> listWithDependencies(Set<String> names, ListOptions additionalListOptions) {
        var visited = new HashSet<String>();
        return listRoles(names, additionalListOptions)
                .expand(role -> {
                    var name = role.getMetadata().getName();
                    if (visited.contains(name)) {
                        return Flux.empty();
                    }
                    if (log.isTraceEnabled()) {
                        log.trace("展开角色：{}", role.getMetadata().getName());
                    }
                    visited.add(name);
                    var annotations = MetadataUtil.nullSafeAnnotations(role);
                    var dependenciesJson = annotations.get(Role.ROLE_DEPENDENCIES_ANNO);
                    var dependencies = stringToList(dependenciesJson);

                    return Flux.fromIterable(dependencies)
                            .filter(dep -> !visited.contains(dep))
                            .collect(Collectors.<String>toSet())
                            .flatMapMany(deps -> listRoles(deps, additionalListOptions));
                })
                .concatWith(Flux.defer(() -> listAggregatedRoles(visited, additionalListOptions)));
    }

    // 查出标记为聚合到这些角色的角色
    private Flux<Role> listAggregatedRoles(Set<String> roleNames, ListOptions additionalListOptions) {
        if (CollectionUtils.isEmpty(roleNames)) {
            return Flux.empty();
        }
        var listOptions = Optional.ofNullable(additionalListOptions)
                .map(ListOptions::builder)
                .orElseGet(ListOptions::builder)
                .andQuery(Queries.in("labels.aggregateToRoles", roleNames))
                .build();
        return client.listAll(Role.class, listOptions, ExtensionUtil.defaultSort());
    }

    // 默认包含隐藏角色
    @Override
    public Flux<Role> list(Set<String> roleNames) {
        return list(roleNames, false);
    }

    // 按名称列出角色并可选排除隐藏项
    @Override
    public Flux<Role> list(Set<String> roleNames, boolean excludeHidden) {
        if (CollectionUtils.isEmpty(roleNames)) {
            return Flux.empty();
        }
        var builder = ListOptions.builder().andQuery(notDeleting()).andQuery(Queries.in("metadata.name", roleNames));
        if (excludeHidden) {
            builder.labelSelector().notEq(Role.HIDDEN_LABEL_NAME, Boolean.TRUE.toString());
        }
        return client.listAll(Role.class, builder.build(), defaultSort());
    }

    // 解析依赖注解中的列表配置
    private List<String> stringToList(String str) {
        if (StringUtils.isBlank(str)) {
            return Collections.emptyList();
        }
        return JsonUtils.jsonToObject(str, new TypeReference<>() {});
    }
}
