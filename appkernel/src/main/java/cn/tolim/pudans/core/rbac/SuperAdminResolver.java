package cn.tolim.pudans.core.rbac;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.contract.extension.index.query.Queries.in;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.InternalReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.DuplicateNameException;

@Component
@RequiredArgsConstructor
public class SuperAdminResolver {

    public static final String BINDING_NAME_SUFFIX = "-" + BuiltinEntities.SUPER_ADMINS_GROUP + "-binding";

    private final ReactiveExtensionClient reactiveClient;

    private final ExtensionClient client;

    private final InternalReactiveExtensionClient internalClient;

    public static String bindingName(String username) {
        return username + BINDING_NAME_SUFFIX;
    }

    public static boolean isSuperAdminBindingName(String name) {
        return name != null && name.endsWith(BINDING_NAME_SUFFIX) && !name.equals(BINDING_NAME_SUFFIX);
    }

    public String ownerOf(String bindingName) {
        if (!isSuperAdminBindingName(bindingName)) {
            return null;
        }
        var username = bindingName.substring(0, bindingName.length() - BINDING_NAME_SUFFIX.length());
        return StringUtils.hasText(username) ? username : null;
    }

    public String ownerOf(UserGroupBinding binding) {
        if (binding == null || binding.getMetadata() == null) {
            return null;
        }
        return ownerOf(binding.getMetadata().getName());
    }

    public boolean isCanonicalBinding(UserGroupBinding binding) {
        if (binding == null || isDeleted(binding)) {
            return false;
        }
        var owner = ownerOf(binding);
        if (owner == null) {
            return false;
        }
        var roleRef = binding.getRoleRef();
        if (roleRef == null
                || !UserGroup.KIND.equals(roleRef.getKind())
                || !BuiltinEntities.SUPER_ADMINS_GROUP.equals(roleRef.getName())) {
            return false;
        }
        var subjects = binding.getSubjects();
        return subjects != null
                && subjects.size() == 1
                && User.KIND.equals(subjects.get(0).getKind())
                && owner.equals(subjects.get(0).getName());
    }

    public Mono<Boolean> hasSuperAdminBinding(String username) {
        if (!StringUtils.hasText(username)) {
            return Mono.just(false);
        }
        return reactiveClient
                .fetch(UserGroupBinding.class, bindingName(username))
                .map(binding -> !isDeleted(binding))
                .defaultIfEmpty(false);
    }

    public boolean hasSuperAdminBindingBlocking(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return client.fetch(UserGroupBinding.class, bindingName(username))
                .filter(binding -> !isDeleted(binding))
                .isPresent();
    }

    public Mono<Boolean> isCanonicalSuperAdmin(String username) {
        if (!StringUtils.hasText(username)) {
            return Mono.just(false);
        }
        return reactiveClient
                .fetch(UserGroupBinding.class, bindingName(username))
                .map(this::isCanonicalBinding)
                .defaultIfEmpty(false)
                .onErrorResume(error -> Mono.just(false));
    }

    public UserGroupBinding canonicalBinding(String username) {
        var binding = UserGroupBinding.membership(username, BuiltinEntities.SUPER_ADMINS_GROUP);
        var metadata = new Metadata();
        metadata.setName(bindingName(username));
        metadata.setFinalizers(Set.of(MetadataUtil.SYSTEM_FINALIZER));
        binding.setMetadata(metadata);
        return binding;
    }

    public UserGroupBinding restore(UserGroupBinding binding, String owner) {
        binding.setSubjects(new LinkedList<>(List.of(UserGroupBinding.Subject.user(owner))));
        binding.setRoleRef(UserGroupBinding.RoleRef.userGroup(BuiltinEntities.SUPER_ADMINS_GROUP));
        binding.getMetadata().setDeletionTimestamp(null);
        return binding;
    }

    public Mono<Void> initialize(String username) {
        return Mono.defer(() -> {
                    var name = bindingName(username);
                    return internalClient
                            .fetch(UserGroupBinding.class, name)
                            .flatMap(existing -> isCanonicalBinding(existing)
                                    ? Mono.just(existing)
                                    : internalClient.updateInternal(restore(existing, username)))
                            .switchIfEmpty(Mono.defer(() -> internalClient
                                    .createInternal(canonicalBinding(username))
                                    .onErrorResume(
                                            DuplicateNameException.class,
                                            e -> internalClient.get(UserGroupBinding.class, name))));
                })
                .then(removeOtherMemberships(username));
    }

    private Mono<Void> removeOtherMemberships(String username) {
        var options = ListOptions.builder()
                .andQuery(in("subjects", UserGroupBinding.Subject.user(username).toString()))
                .build();
        return reactiveClient
                .listAll(UserGroupBinding.class, options, defaultSort())
                .filter(binding -> Optional.ofNullable(binding.getRoleRef())
                        .map(ref -> UserGroup.KIND.equals(ref.getKind()))
                        .orElse(false))
                .filter(binding -> !BuiltinEntities.SUPER_ADMINS_GROUP.equals(
                        binding.getRoleRef().getName()))
                .filter(binding -> !isDeleted(binding))
                .flatMap(reactiveClient::delete)
                .then();
    }

    public boolean hasOtherCanonicalSuperAdmin(String username) {
        var options = ListOptions.builder().andQuery(notDeleting()).build();
        return client.listAll(UserGroupBinding.class, options, defaultSort()).stream()
                .filter(this::isCanonicalBinding)
                .map(this::ownerOf)
                .anyMatch(owner -> owner != null && !owner.equals(username));
    }
}
