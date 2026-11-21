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
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class Groups {

    private final ReactiveExtensionClient client;

    private final RbacCache rbacCache;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    private final DomainEventPublisher domainEventPublisher;

    public Mono<UserGroup> getUserGroup(String name) {
        return client.get(UserGroup.class, name);
    }

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

    

    public Mono<UserGroup> getUserGroupByUsername(String username) {
        return listMembershipBindings(username)
                .next()
                .map(UserGroupBinding::getRoleRef)
                .map(UserGroupBinding.RoleRef::getName)
                .flatMap(this::getUserGroup);
    }

    

    public Flux<UserGroupBinding> listMembershipBindings(String username) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("subjects", UserGroupBinding.Subject.user(username).toString()))
                .build();
        return client.listAll(UserGroupBinding.class, options, defaultSort())
                .filter(Groups::isMembership);
    }

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
                        (left, right) -> left))
                .flatMap(groupsByUser -> listUserGroups(groupsByUser.values())
                        .collectMap(group -> group.getMetadata().getName())
                        .map(groups -> groupsByUser.entrySet().stream()
                                .filter(entry -> groups.containsKey(entry.getValue()))
                                .collect(Collectors.toMap(
                                        entry -> entry.getKey(), entry -> groups.get(entry.getValue())))));
    }

    

    public Flux<String> listRoleNamesOfGroup(UserGroup group) {
        var fromGroup = Flux.fromIterable(Optional.ofNullable(group.getRoleRefs()).orElseGet(List::of))
                .filter(ref -> Role.KIND.equals(ref.getKind()))
                .map(UserGroup.RoleRef::getName);
        var fromBindings = listGroupRoleBindings(group.getMetadata().getName())
                .map(binding -> binding.getRoleRef().getName());
        return Flux.concat(fromGroup, fromBindings).filter(StringUtils::hasText).distinct();
    }

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

    

    public Flux<String> getRoleNamesByUsername(String username) {
        var loader = getUserGroupByUsername(username)
                .flatMapMany(this::listRoleNamesOfGroup)
                .distinct()
                .collectList();
        return rbacCache.cachedUserRoles(username, loader).flatMapMany(Flux::fromIterable);
    }

    public Flux<Role> getRolesByUsername(String username) {
        return getRoleNamesByUsername(username)
                .collect(CollectorsHolder.set())
                .flatMapMany(names -> names.isEmpty()
                        ? Flux.empty()
                        : client.listAll(
                                Role.class,
                                ListOptions.builder()
                                        .andQuery(notDeleting())
                                        .andQuery(in("metadata.name", names))
                                        .build(),
                                defaultSort()));
    }

    

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
                        return Mono.just(existing.get(0));
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
                                .thenReturn(existing.stream()
                                        .filter(binding -> groupName.equals(binding.getRoleRef().getName()))
                                        .findFirst()
                                        .orElseThrow())
                                .doOnNext(binding -> publishGroupChanged(username, fromGroup, groupName));
                    }
                    var binding = UserGroupBinding.membership(username, groupName);
                    var metadata = new cn.tolim.pudans.extension.contract.Metadata();
                    metadata.setName(username + "-" + groupName + "-binding");
                    binding.setMetadata(metadata);
                    return client.create(binding)
                            .flatMap(created -> deleteOthers
                                    .then(rbacCache.evictUserRoles(username))
                                    .thenReturn(created))
                            .doOnNext(created -> publishGroupChanged(username, fromGroup, groupName));
                });
    }

    public Mono<Void> removeUserGroup(String username) {
        return builtinEntityPolicy
                .checkUserGroupRemoval(username)
                .then(listMembershipBindings(username).collectList())
                .flatMap(bindings -> {
                    if (bindings.isEmpty()) {
                        return Mono.empty();
                    }
                    var fromGroup = bindings.get(0).getRoleRef().getName();
                    return Flux.fromIterable(bindings)
                            .flatMap(client::delete)
                            .then()
                            .doOnSuccess(ignored -> publishGroupChanged(username, fromGroup, null));
                })
                .then(rbacCache.evictUserRoles(username));
    }

    public Flux<Membership> listMemberships() {
        var options = ListOptions.builder().andQuery(notDeleting()).build();
        return client.listAll(UserGroupBinding.class, options, defaultSort())
                .filter(Groups::isMembership)
                .map(binding -> new Membership(memberUsername(binding), binding.getRoleRef().getName()))
                .filter(membership -> StringUtils.hasText(membership.username()))
                .distinct();
    }

    public record Membership(String username, String groupName) {}

    private static boolean isMembership(UserGroupBinding binding) {
        return Optional.ofNullable(binding.getRoleRef())
                .map(ref -> UserGroup.KIND.equals(ref.getKind())
                        && UserGroup.GROUP.equals(ref.getApiGroup()))
                .orElse(false);
    }

    private static String memberUsername(UserGroupBinding binding) {
        return Optional.ofNullable(binding.getSubjects())
                .stream()
                .flatMap(Collection::stream)
                .filter(subject -> User.KIND.equals(subject.getKind()))
                .map(UserGroupBinding.Subject::getName)
                .findFirst()
                .orElse("");
    }

    private void publishGroupChanged(String username, String fromGroup, String toGroup) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", username);
        payload.put("fromGroup", fromGroup);
        payload.put("toGroup", toGroup);
        domainEventPublisher.publish("user.group.changed", payload, null);
    }

    
    private static final class CollectorsHolder {
        static java.util.stream.Collector<String, ?, Set<String>> set() {
            return java.util.stream.Collectors.toSet();
        }
    }
}
