package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class LocalAccounts {

    private final Accounts accounts;

    private final Groups groups;

    private final ParameterGroups parameterGroups;

    private final DomainEventPublisher domainEventPublisher;

    private final ReactiveTransactionManager transactionManager;

    public Mono<User> create(
            String username,
            String displayName,
            String email,
            String rawPassword,
            String avatar,
            String userGroup) {
        return Mono.defer(() -> Mono.fromCallable(() -> accounts.encryptPassword(rawPassword))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(encodedPassword -> {
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
                            spec.setPassword(encodedPassword);
                            spec.setEmailVerified(true);
                            spec.setDisabled(false);
                            user.setSpec(spec);
                            return accounts.createUser(user);
                        }))
                .flatMap(user -> resolveGroup(userGroup)
                        .flatMap(group -> groups.assignUserGroup(username, group))
                        .thenReturn(user))
                .as(TransactionalOperator.create(transactionManager)::transactional)
                .doOnNext(user -> domainEventPublisher.publish("user.created", UserEventPayloads.created(user), null));
    }

    private Mono<String> resolveGroup(String userGroup) {
        if (StringUtils.hasText(userGroup)) {
            return Mono.just(userGroup);
        }
        return parameterGroups
                .getSession()
                .map(session -> StringUtils.hasText(session.getDefaultUserGroup())
                        ? session.getDefaultUserGroup()
                        : BuiltinEntities.NORMAL_USERS_GROUP)
                .defaultIfEmpty(BuiltinEntities.NORMAL_USERS_GROUP);
    }
}
