package cn.tolim.pudans.core.rbac.service;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.contract.extension.index.query.Queries.in;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final ReactiveExtensionClient client;

    private final RbacCacheService rbacCacheService;

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
                .filter(binding -> Optional.ofNullable(binding.getRoleRef())
                        .map(ref -> UserGroup.KIND.equals(ref.getKind())
                                && UserGroup.GROUP.equals(ref.getApiGroup()))
                        .orElse(false));
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
        return rbacCacheService.cachedUserRoles(username, loader).flatMapMany(Flux::fromIterable);
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
            return Mono.error(new ServerWebInputException("User group name must not be blank."));
        }
        return builtinEntityPolicy
                .checkUserGroupAssignment(username, groupName)
                .then(client.fetch(User.class, username))
                .switchIfEmpty(Mono.error(new ServerWebInputException("User [" + username + "] is not found.")))
                .then(getUserGroup(groupName))
                .switchIfEmpty(
                        Mono.error(new ServerWebInputException("User group [" + groupName + "] is not found.")))
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
                    return deleteOthers.then(Mono.defer(() -> {
                        var binding = UserGroupBinding.membership(username, groupName);
                        var metadata = new cn.tolim.pudans.contract.extension.Metadata();
                        metadata.setName(username + "-" + groupName + "-binding");
                        binding.setMetadata(metadata);
                        return client.create(binding);
                    })).flatMap(binding -> rbacCacheService.evictUserRoles(username).thenReturn(binding))
                            .doOnNext(binding -> publishGroupChanged(username, fromGroup, groupName));
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
                .then(rbacCacheService.evictUserRoles(username));
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
