package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.extension.user.User.UserSpec;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.InternalReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntityDefinitions;
import cn.tolim.pudans.core.rbac.SuperAdminResolver;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
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

    @Override
    public Mono<Void> initialize(InitializationParam param) {
        return ensureRoles()
                .then(ensureGroups())
                .then(ensureSuperAdmin(param))
                .flatMap(this::ensureSystemFinalizer)
                .flatMap(this::bindSuperAdmin)
                .then();
    }

    Mono<Void> ensureRoles() {
        return Flux.just(
                        BuiltinEntityDefinitions.superRole(),
                        BuiltinEntityDefinitions.systemAdminRole(),
                        BuiltinEntityDefinitions.normalRole())
                .concatMap(this::ensureRole)
                .then();
    }

    Mono<Void> ensureGroups() {
        return Flux.just(
                        BuiltinEntityDefinitions.superAdminsGroup(),
                        BuiltinEntityDefinitions.systemAdminsGroup(),
                        BuiltinEntityDefinitions.normalUsersGroup())
                .concatMap(this::ensureGroup)
                .then();
    }

    Mono<User> ensureSuperAdmin(InitializationParam param) {
        return client.fetch(User.class, param.getUsername())
                .flatMap(admin -> verifyExistingAdmin(admin, param))
                .switchIfEmpty(Mono.defer(() -> client.create(createAdmin(param))
                        .doOnNext(this::publishCreated)
                        .onErrorResume(DuplicateNameException.class, e -> client.get(User.class, param.getUsername())
                                .flatMap(admin -> verifyExistingAdmin(admin, param)))));
    }

    Mono<User> ensureSystemFinalizer(User user) {
        if (!ExtensionUtil.addFinalizers(user.getMetadata(), Set.of(MetadataUtil.SYSTEM_FINALIZER))) {
            return Mono.just(user);
        }
        return client.updateInternal(user);
    }

    Mono<User> bindSuperAdmin(User user) {
        return superAdminResolver
                .initialize(user.getMetadata().getName())
                .thenReturn(user);
    }

    private Mono<Role> ensureRole(Role role) {
        var name = role.getMetadata().getName();
        return client.fetch(Role.class, name)
                .switchIfEmpty(Mono.defer(() -> client.create(role)
                        .onErrorResume(DuplicateNameException.class, e -> client.get(Role.class, name))));
    }

    private Mono<UserGroup> ensureGroup(UserGroup group) {
        var name = group.getMetadata().getName();
        return client.fetch(UserGroup.class, name)
                .switchIfEmpty(Mono.defer(() -> client.create(group)
                        .onErrorResume(DuplicateNameException.class, e -> client.get(UserGroup.class, name))));
    }

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

    private void publishCreated(User user) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("displayName", user.getSpec().getDisplayName());
        payload.put("email", user.getSpec().getEmail());
        payload.put("source", "system");
        domainEventPublisher.publish("user.created", payload, null);
    }

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
        spec.setEmail(param.getEmail() == null
                ? null
                : param.getEmail().trim().toLowerCase(Locale.ROOT));
        spec.setPassword(passwordEncoder.encode(param.getPassword()));

        var user = new User();
        user.setMetadata(metadata);
        user.setSpec(spec);
        return user;
    }
}
