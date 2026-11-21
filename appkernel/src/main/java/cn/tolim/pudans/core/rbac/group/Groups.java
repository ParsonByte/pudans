package cn.tolim.pudans.core.rbac.group;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.contract.index.query.Queries.in;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import cn.tolim.pudans.core.rbac.SessionInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.core.rbac.permission.RbacCache;

@Service
@RequiredArgsConstructor
public class Groups {

    private final ReactiveExtensionClient client;

    private final RbacCache rbacCache;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    private final DomainEventPublisher domainEventPublisher;

    private final SessionInvalidator sessionInvalidator;

    // 按名称读取用户组，不存在时返回空。
    public Mono<UserGroup> getUserGroup(String name) {
        return client.get(UserGroup.class, name);
    }

    // 批量列出未删除的用户组。
    public Flux<UserGroup> listUserGroups(Collection<String> names) {
        if (CollectionUtils.isEmpty(names)) {
            return Flux.empty();
        }
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("metadata.name", new HashSet<>(names)))
                .build();
        return client.listAll(UserGroup.class, options, defaultSort());
    }

    

    // 通过成员绑定反查用户所属用户组。
    public Mono<UserGroup> getUserGroupByUsername(String username) {
        return listMembershipBindings(username)
                .next()
                .map(UserGroupBinding::getRoleRef)
                .map(UserGroupBinding.RoleRef::getName)
                .flatMap(this::getUserGroup);
    }

    

    // 列出用户作为主体的未删除成员绑定。
    public Flux<UserGroupBinding> listMembershipBindings(String username) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("subjects", UserGroupBinding.Subject.user(username).toString()))
                .build();
        return client.listAll(UserGroupBinding.class, options, defaultSort())
                .filter(Groups::isMembership);
    }

    // 列出指定用户组中的用户名并去重。
    public Flux<String> listUsernamesOfGroups(Collection<String> groupNames) {
        if (CollectionUtils.isEmpty(groupNames)) {
            return Flux.empty();
        }
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("roleRef.name", new HashSet<>(groupNames)))
                .build();
        return client.listAll(UserGroupBinding.class, options, defaultSort())
                .filter(Groups::isMembership)
                .map(Groups::memberUsername)
                .filter(StringUtils::hasText)
                .distinct();
    }

    // 批量把用户名映射到其所属用户组。
    public Mono<Map<String, UserGroup>> getGroupsByUsernames(Collection<String> usernames) {
        if (CollectionUtils.isEmpty(usernames)) {
            return Mono.just(Map.of());
        }
        var subjects = usernames.stream()
                .map(username -> UserGroupBinding.Subject.user(username).toString())
                .collect(Collectors.toSet());
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("subjects", subjects))
                .build();
        return client.listAll(UserGroupBinding.class, options, defaultSort())
                .filter(Groups::isMembership)
                .filter(binding -> StringUtils.hasText(memberUsername(binding)))
                .collect(Collectors.toMap(
                        Groups::memberUsername,
                        binding -> binding.getRoleRef().getName(),
                        (left, ignored) -> left))
                .flatMap(groupsByUser -> listUserGroups(groupsByUser.values())
                        .collectMap(group -> group.getMetadata().getName())
                        .map(groups -> groupsByUser.entrySet().stream()
                                .filter(entry -> groups.containsKey(entry.getValue()))
                                .collect(Collectors.toMap(
                                        entry -> entry.getKey(), entry -> groups.get(entry.getValue())))));
    }

    

    // 合并用户组直接引用与绑定授予的角色名。
    public Flux<String> listRoleNamesOfGroup(UserGroup group) {
        var fromGroup = Flux.fromIterable(Optional.ofNullable(group.getRoleRefs()).orElseGet(List::of))
                .filter(ref -> Role.KIND.equals(ref.getKind()))
                .map(UserGroup.RoleRef::getName);
        var fromBindings = listGroupRoleBindings(group.getMetadata().getName())
                .map(binding -> binding.getRoleRef().getName());
        return Flux.concat(fromGroup, fromBindings).filter(StringUtils::hasText).distinct();
    }

    // 列出以该用户组为主体且引用角色的绑定。
    public Flux<UserGroupBinding> listGroupRoleBindings(String groupName) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("subjects", UserGroupBinding.Subject.userGroup(groupName).toString()))
                .build();
        return client.listAll(UserGroupBinding.class, options, defaultSort())
                .filter(binding -> Optional.ofNullable(binding.getRoleRef())
                        .map(ref -> Role.KIND.equals(ref.getKind()))
                        .orElse(false));
    }

    

    // 经缓存返回用户通过用户组获得的角色名。
    public Flux<String> getRoleNamesByUsername(String username) {
        var loader = getUserGroupByUsername(username)
                .flatMapMany(this::listRoleNamesOfGroup)
                .distinct()
                .collectList();
        return rbacCache.cachedUserRoles(username, loader).flatMapMany(Flux::fromIterable);
    }

    // 把用户切换到目标组，保证成员关系唯一。
    public Mono<UserGroupBinding> assignUserGroup(String username, String groupName) {
        if (!StringUtils.hasText(groupName)) {
            return Mono.error(new ServerWebInputException("用户组名称不能为空。"));
        }
        return builtinEntityPolicy
                .checkUserGroupAssignment(username, groupName)
                .then(client.fetch(User.class, username))
                .switchIfEmpty(Mono.error(new ServerWebInputException("未找到用户 [" + username + "]。")))
                .then(getUserGroup(groupName))
                .switchIfEmpty(
                        Mono.error(new ServerWebInputException("未找到用户组 [" + groupName + "]。")))
                .then(listMembershipBindings(username).collectList())
                .flatMap(existing -> {
                    var alreadyAssigned = existing.stream()
                            .anyMatch(binding -> groupName.equals(binding.getRoleRef().getName()));
                    if (alreadyAssigned && existing.size() == 1) {
                        return Mono.just(existing.getFirst());
                    }
                    var fromGroup = existing.stream()
                            .map(binding -> binding.getRoleRef().getName())
                            .findFirst()
                            .orElse(null);
                    var deleteOthers = Flux.fromIterable(existing)
                            .filter(binding -> !groupName.equals(binding.getRoleRef().getName()))
                            .flatMap(client::delete)
                            .then();
                    if (alreadyAssigned) {
                        return deleteOthers
                                .then(rbacCache.evictUserRoles(username))
                                .then(invalidateSessions(username))
                                .thenReturn(existing.stream()
                                        .filter(binding -> groupName.equals(binding.getRoleRef().getName()))
                                        .findFirst()
                                        .orElseThrow())
                                .doOnNext(ignored -> publishGroupChanged(username, fromGroup, groupName));
                    }
                    var binding = UserGroupBinding.membership(username, groupName);
                    var metadata = new cn.tolim.pudans.extension.contract.Metadata();
                    metadata.setName(username + "-" + groupName + "-binding");
                    binding.setMetadata(metadata);
                    return client.create(binding)
                            .flatMap(created -> deleteOthers
                                    .then(rbacCache.evictUserRoles(username))
                                    .then(invalidateSessions(username))
                                    .thenReturn(created))
                            .doOnNext(ignored -> publishGroupChanged(username, fromGroup, groupName));
                });
    }

    // 移除用户的全部成员绑定并清理角色缓存。
    public Mono<Void> removeUserGroup(String username) {
        return builtinEntityPolicy
                .checkUserGroupRemoval(username)
                .then(listMembershipBindings(username).collectList())
                .flatMap(bindings -> {
                    if (bindings.isEmpty()) {
                        return Mono.empty();
                    }
                    var fromGroup = bindings.getFirst().getRoleRef().getName();
                    return Flux.fromIterable(bindings)
                            .flatMap(client::delete)
                            .then()
                            .doOnSuccess(ignored -> publishGroupChanged(username, fromGroup, null));
                })
                .then(rbacCache.evictUserRoles(username))
                .then(invalidateSessions(username));
    }

    // 角色变化后立即失效已有会话，避免旧权限继续使用。
    private Mono<Void> invalidateSessions(String username) {
        return sessionInvalidator.invalidate(username);
    }

    // 列出全部有效的用户与用户组成员关系。
    public Flux<Membership> listMemberships() {
        var options = ListOptions.builder().andQuery(notDeleting()).build();
        return client.listAll(UserGroupBinding.class, options, defaultSort())
                .filter(Groups::isMembership)
                .map(binding -> new Membership(memberUsername(binding), binding.getRoleRef().getName()))
                .filter(membership -> StringUtils.hasText(membership.username()))
                .distinct();
    }

    public record Membership(String username, String groupName) {}

    // 判断绑定是否表示用户与用户组的成员关系。
    private static boolean isMembership(UserGroupBinding binding) {
        return Optional.ofNullable(binding.getRoleRef())
                .map(ref -> UserGroup.KIND.equals(ref.getKind())
                        && UserGroup.GROUP.equals(ref.getApiGroup()))
                .orElse(false);
    }

    // 取绑定中第一个用户主体名，缺失时返回空串。
    private static String memberUsername(UserGroupBinding binding) {
        return Optional.ofNullable(binding.getSubjects())
                .stream()
                .flatMap(Collection::stream)
                .filter(subject -> User.KIND.equals(subject.getKind()))
                .map(UserGroupBinding.Subject::getName)
                .findFirst()
                .orElse("");
    }

    // 发布用户组变更事件，供下游刷新派生数据。
    private void publishGroupChanged(String username, String fromGroup, String toGroup) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", username);
        payload.put("fromGroup", fromGroup);
        payload.put("toGroup", toGroup);
        domainEventPublisher.publish("user.group.changed", payload, null);
    }
}
