package cn.tolim.pudans.core.user.service;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class LocalUserService {

    private final UserService userService;

    private final UserGroupService userGroupService;

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final DomainEventPublisher domainEventPublisher;

    private final ReactiveTransactionManager transactionManager;

    public Mono<User> create(
            String username,
            String displayName,
            String email,
            String rawPassword,
            String avatar,
            String userGroup) {
        return Mono.defer(() -> {
                    var user = new User();
                    var metadata = new Metadata();
                    metadata.setName(username);
                    user.setMetadata(metadata);
                    var spec = new User.UserSpec();
                    spec.setUid(AuditIdGenerator.next());
                    spec.setDisplayName(StringUtils.hasText(displayName)
                            ? displayName.trim()
                            : username);
                    spec.setEmail(StringUtils.hasText(email)
                            ? email.trim().toLowerCase(Locale.ROOT)
                            : null);
                    spec.setAvatar(avatar);
                    spec.setRegisteredAt(Instant.now());
                    spec.setPassword(userService.encryptPassword(rawPassword));
                    spec.setEmailVerified(true);
                    spec.setDisabled(false);
                    user.setSpec(spec);
                    return userService.createUser(user);
                })
                .flatMap(user -> resolveGroup(userGroup)
                        .flatMap(group -> userGroupService.assignUserGroup(username, group))
                        .thenReturn(user))
                .as(TransactionalOperator.create(transactionManager)::transactional)
                .doOnNext(user -> domainEventPublisher.publish("user.created", UserEventPayloads.created(user), null));
    }

    private Mono<String> resolveGroup(String userGroup) {
        if (StringUtils.hasText(userGroup)) {
            return Mono.just(userGroup);
        }
        return parameterSettingGroupService
                .getSession()
                .map(session -> StringUtils.hasText(session.getDefaultUserGroup())
                        ? session.getDefaultUserGroup()
                        : BuiltinEntities.NORMAL_USERS_GROUP)
                .defaultIfEmpty(BuiltinEntities.NORMAL_USERS_GROUP);
    }
}
