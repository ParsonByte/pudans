package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.User.UserSpec;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;
import cn.tolim.pudans.core.rbac.SuperAdminResolver;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SystemInitializer implements SuperAdminInitializer {

    private final InternalReactiveExtensionClient client;

    private final PasswordEncoder passwordEncoder;

    private final SuperAdminResolver superAdminResolver;

    private final DomainEventPublisher domainEventPublisher;

    private final BuiltinRoleDefinitions builtinRoleDefinitions;

    // 依次补齐内置角色、用户组、超级管理员并完成最终化与绑定。
    @Override
    public Mono<Void> initialize(InitializationParam param) {
        return ensureRoles()
                .then(ensureGroups())
                .then(ensureSuperAdmin(param))
                .flatMap(this::ensureSystemFinalizer)
                .flatMap(this::bindSuperAdmin)
                .then();
    }

    // 幂等创建全部内置角色，已存在的角色保持不变。
    Mono<Void> ensureRoles() {
        return Flux.fromIterable(builtinRoleDefinitions.builtinRoles())
                .concatMap(this::ensureRole)
                .then();
    }

    // 幂等创建全部内置用户组，已存在的用户组保持不变。
    Mono<Void> ensureGroups() {
        return Flux.fromIterable(builtinRoleDefinitions.builtinGroups())
                .concatMap(this::ensureGroup)
                .then();
    }

    // 复用已有管理员或新建，并发创建冲突时回查确认。
    Mono<User> ensureSuperAdmin(InitializationParam param) {
        return client.fetch(User.class, param.getUsername())
                .flatMap(admin -> verifyExistingAdmin(admin, param))
                .switchIfEmpty(Mono.defer(() -> client.create(createAdmin(param))
                        .doOnNext(this::publishCreated)
                        .onErrorResume(DuplicateNameException.class, e -> client.get(User.class, param.getUsername())
                                .flatMap(admin -> verifyExistingAdmin(admin, param)))));
    }

    // 为管理员补上系统终结器，防止被误删除。
    Mono<User> ensureSystemFinalizer(User user) {
        if (!ExtensionUtil.addFinalizers(user.getMetadata(), Set.of(MetadataUtil.SYSTEM_FINALIZER))) {
            return Mono.just(user);
        }
        return client.updateInternal(user);
    }

    // 将管理员登记为超级管理员，使权限立即生效。
    Mono<User> bindSuperAdmin(User user) {
        return superAdminResolver
                .initialize(user.getMetadata().getName())
                .thenReturn(user);
    }

    // 按名称查找角色，缺失时才创建，避免重复写入。
    private Mono<Role> ensureRole(Role role) {
        var name = role.getMetadata().getName();
        return client.fetch(Role.class, name)
                .switchIfEmpty(Mono.defer(() -> client.create(role)
                        .onErrorResume(DuplicateNameException.class, e -> client.get(Role.class, name))));
    }

    // 按名称查找用户组，缺失时才创建，避免重复写入。
    private Mono<UserGroup> ensureGroup(UserGroup group) {
        var name = group.getMetadata().getName();
        return client.fetch(UserGroup.class, name)
                .switchIfEmpty(Mono.defer(() -> client.create(group)
                        .onErrorResume(DuplicateNameException.class, e -> client.get(UserGroup.class, name))));
    }

    // 校验已有管理员可登录且密码一致，否则拒绝初始化。
    private Mono<User> verifyExistingAdmin(User admin, InitializationParam param) {
        return Mono.fromCallable(() -> {
            var spec = admin.getSpec();
            if (Boolean.parseBoolean(MetadataUtil.nullSafeLabels(admin).get(User.HIDDEN_USER_LABEL))
                    || admin.getMetadata().getDeletionTimestamp() != null
                    || Boolean.TRUE.equals(spec.getDisabled())
                    || !StringUtils.hasText(spec.getPassword())
                    || !passwordEncoder.matches(param.getPassword(), spec.getPassword())) {
                throw new BadCredentialsException("用户名或密码错误。");
            }
            return admin;
        });
    }

    // 发布用户创建事件，供审计与下游逻辑消费。
    private void publishCreated(User user) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("displayName", user.getSpec().getDisplayName());
        payload.put("email", user.getSpec().getEmail());
        payload.put("source", "system");
        domainEventPublisher.publish("user.created", payload, null);
    }

    // 组装管理员用户对象，密码加密后写入用户规格。
    User createAdmin(InitializationParam param) {
        var metadata = new Metadata();
        metadata.setName(param.getUsername());
        metadata.setFinalizers(Set.of(MetadataUtil.SYSTEM_FINALIZER));

        var spec = new UserSpec();
        spec.setUid(AuditIdGenerator.next());
        spec.setDisplayName("Administrator");
        spec.setDisabled(false);
        spec.setRegisteredAt(Instant.now());
        spec.setTwoFactorAuthEnabled(false);
        spec.setPassword(passwordEncoder.encode(param.getPassword()));

        var user = new User();
        user.setMetadata(metadata);
        user.setSpec(spec);
        return user;
    }
}
