package cn.tolim.pudans.core.rbac;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.contract.index.query.Queries.in;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.DuplicateNameException;

@Component
@RequiredArgsConstructor
public class SuperAdminResolver {

    public static final String BINDING_NAME_SUFFIX = "-" + BuiltinEntities.SUPER_ADMINS_GROUP + "-binding";

    private final ReactiveExtensionClient reactiveClient;

    private final ExtensionClient client;

    private final InternalReactiveExtensionClient internalClient;

    // 按用户名生成超管成员绑定的规范名称。
    public static String bindingName(String username) {
        return username + BINDING_NAME_SUFFIX;
    }

    // 判断名称是否具有超管成员绑定的后缀特征。
    public static boolean isSuperAdminBindingName(String name) {
        return name != null && name.endsWith(BINDING_NAME_SUFFIX) && !name.equals(BINDING_NAME_SUFFIX);
    }

    // 从规范绑定名反解用户名，非规范名返回空。
    public String ownerOf(String bindingName) {
        if (!isSuperAdminBindingName(bindingName)) {
            return null;
        }
        var username = bindingName.substring(0, bindingName.length() - BINDING_NAME_SUFFIX.length());
        return StringUtils.hasText(username) ? username : null;
    }

    // 从绑定对象读取所属用户名，缺失时返回空。
    public String ownerOf(UserGroupBinding binding) {
        if (binding == null || binding.getMetadata() == null) {
            return null;
        }
        return ownerOf(binding.getMetadata().getName());
    }

    // 规范绑定必须未删除且主体与角色引用一致。
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

    // 异步判断用户是否存在未删除的超管绑定。
    public Mono<Boolean> hasSuperAdminBinding(String username) {
        if (!StringUtils.hasText(username)) {
            return Mono.just(false);
        }
        return reactiveClient
                .fetch(UserGroupBinding.class, bindingName(username))
                .map(binding -> !isDeleted(binding))
                .defaultIfEmpty(false);
    }

    // 同步判断规范超管绑定，供协调器删除时使用。
    public boolean hasCanonicalBindingBlocking(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        // 删除依据必须来自规范绑定，伪造名字的绑定不得触发对其它绑定的清理
        return client.fetch(UserGroupBinding.class, bindingName(username))
                .filter(this::isCanonicalBinding)
                .isPresent();
    }

    // 判断用户是否为规范超管，查询异常按否处理。
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

    // 构造受系统终结器保护的规范超管成员绑定。
    public UserGroupBinding canonicalBinding(String username) {
        var binding = UserGroupBinding.membership(username, BuiltinEntities.SUPER_ADMINS_GROUP);
        var metadata = new Metadata();
        metadata.setName(bindingName(username));
        metadata.setFinalizers(Set.of(MetadataUtil.SYSTEM_FINALIZER));
        binding.setMetadata(metadata);
        return binding;
    }

    // 把被篡改的绑定恢复为规范主体与角色引用。
    public UserGroupBinding restore(UserGroupBinding binding, String owner) {
        binding.setSubjects(new LinkedList<>(List.of(UserGroupBinding.Subject.user(owner))));
        binding.setRoleRef(UserGroupBinding.RoleRef.userGroup(BuiltinEntities.SUPER_ADMINS_GROUP));
        binding.getMetadata().setDeletionTimestamp(null);
        return binding;
    }

    // 确保超管只保留规范绑定并清理其它组成员关系。
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

    // 删除超管用户在其他用户组中的成员绑定。
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

    // 判断除指定用户外是否还有其它规范超管。
    public boolean hasOtherCanonicalSuperAdmin(String username) {
        var options = ListOptions.builder().andQuery(notDeleting()).build();
        return client.listAll(UserGroupBinding.class, options, defaultSort()).stream()
                .filter(this::isCanonicalBinding)
                .map(this::ownerOf)
                .anyMatch(owner -> owner != null && !owner.equals(username));
    }
}
