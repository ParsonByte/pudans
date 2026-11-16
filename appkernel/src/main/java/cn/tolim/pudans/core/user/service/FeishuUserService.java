package cn.tolim.pudans.core.user.service;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.contract.extension.index.query.Queries.in;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.extension.user.UserConnection;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuUserService {

    public static final String PROVIDER = "feishu";

    private final ReactiveExtensionClient client;

    private final UserService userService;

    private final UserGroupService userGroupService;

    private final DomainEventPublisher domainEventPublisher;

    private final ReactiveTransactionManager transactionManager;

    private final ParameterSettingGroupService parameterSettingGroupService;

    private Clock clock = Clock.systemUTC();

    public Mono<User> findByProviderUserId(String providerUserId) {
        return findConnection(providerUserId).flatMap(connection -> userService.getUser(
                connection.getSpec().getUsername()));
    }

    public Mono<UserConnection> findConnection(String providerUserId) {
        if (!StringUtils.hasText(providerUserId)) {
            return Mono.empty();
        }
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("spec.providerUserId", providerUserId))
                .build();
        return client.listAll(UserConnection.class, options, defaultSort())
                .filter(connection -> PROVIDER.equals(connection.getSpec().getRegistrationId()))
                .next();
    }

    public Flux<UserConnection> listAllConnections() {
        var options = ListOptions.builder().andQuery(notDeleting()).build();
        return client.listAll(UserConnection.class, options, defaultSort())
                .filter(connection -> PROVIDER.equals(connection.getSpec().getRegistrationId()));
    }

    public Flux<UserConnection> listConnections(String username) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("spec.username", username))
                .build();
        return client.listAll(UserConnection.class, options, defaultSort())
                .filter(connection -> PROVIDER.equals(connection.getSpec().getRegistrationId()));
    }

    

    public Mono<User> createFromFeishu(FeishuUserData data, String userGroupName) {
        if (data == null || (!StringUtils.hasText(data.unionId()) && !StringUtils.hasText(data.openId()))) {
            return Mono.error(new ServerWebInputException(
                    "A Feishu union_id or open_id is required to provision a local user."));
        }
        if (!StringUtils.hasText(data.displayName()) || !StringUtils.hasText(data.email())) {
            return Mono.error(new ServerWebInputException(
                    "displayName and email must come from Feishu and are required."));
        }
        var providerUserId =
                StringUtils.hasText(data.unionId()) ? data.unionId() : data.openId();
        return findConnection(providerUserId)
                .flatMap(existing -> Mono.<User>error(new ServerWebInputException(
                        "Feishu identity [" + providerUserId + "] is already bound to user ["
                                + existing.getSpec().getUsername() + "].")))
                .then(Mono.defer(() -> {
                    var username = StringUtils.hasText(data.username())
                            ? data.username()
                            : sanitizeUsername(providerUserId);
                    var user = new User();
                    var metadata = new Metadata();
                    metadata.setName(username);
                    user.setMetadata(metadata);
                    var spec = new User.UserSpec();
                    spec.setUid(AuditIdGenerator.next());
                    spec.setDisplayName(data.displayName());
                    spec.setEmail(data.email().toLowerCase());
                    spec.setEmailVerified(true);
                    spec.setAvatar(data.avatar());
                    spec.setRegisteredAt(clock.instant());
                    spec.setPassword(null);
                    user.setSpec(spec);
                    return userService.createUser(user);
                }))
                .flatMap(user -> createConnection(user, data, providerUserId).thenReturn(user))
                .flatMap(user -> StringUtils.hasText(userGroupName)
                        ? userGroupService.assignUserGroup(user.getMetadata().getName(), userGroupName)
                                .thenReturn(user)
                        : assignDefaultUserGroup(user.getMetadata().getName()).thenReturn(user))
                .as(TransactionalOperator.create(transactionManager)::transactional)
                .doOnNext(user -> domainEventPublisher.publish("user.created", userPayload(user), null));
    }

    private static java.util.Map<String, Object> userPayload(User user) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("displayName", user.getSpec().getDisplayName());
        payload.put("email", user.getSpec().getEmail());
        return payload;
    }

    public Mono<User> createFromFeishu(FeishuUserData data) {
        return createFromFeishu(data, null);
    }

    Mono<Void> assignDefaultUserGroup(String username) {
        return parameterSettingGroupService
                .getSession()
                .map(session -> StringUtils.hasText(session.getDefaultUserGroup())
                        ? session.getDefaultUserGroup()
                        : BuiltinEntities.NORMAL_USERS_GROUP)
                .defaultIfEmpty(BuiltinEntities.NORMAL_USERS_GROUP)
                .onErrorResume(error -> Mono.just(BuiltinEntities.NORMAL_USERS_GROUP))
                .flatMap(groupName -> client.fetch(UserGroup.class, groupName)
                        .switchIfEmpty(Mono.defer(
                                () -> client.fetch(UserGroup.class, BuiltinEntities.NORMAL_USERS_GROUP))))
                .flatMap(group -> userGroupService.assignUserGroup(username, group.getMetadata().getName()))
                .then();
    }

    private Mono<UserConnection> createConnection(User user, FeishuUserData data, String providerUserId) {
        var connection = new UserConnection();
        var metadata = new Metadata();
        metadata.setName(user.getMetadata().getName() + "-feishu");
        connection.setMetadata(metadata);
        var spec = new UserConnection.UserConnectionSpec();
        spec.setRegistrationId(PROVIDER);
        spec.setUsername(user.getMetadata().getName());
        spec.setProviderUserId(providerUserId);
        spec.setUpdatedAt(clock.instant());
        connection.setSpec(spec);
        return client.create(connection);
    }

    

    public Mono<User> syncProfile(String username, String displayName, String email) {
        if (!StringUtils.hasText(displayName) && !StringUtils.hasText(email)) {
            return Mono.empty();
        }
        return userService.getUser(username).flatMap(user -> {
            var changedFields = new ArrayList<String>();
            if (StringUtils.hasText(displayName)
                    && !Objects.equals(displayName, user.getSpec().getDisplayName())) {
                user.getSpec().setDisplayName(displayName);
                changedFields.add("displayName");
            }
            if (StringUtils.hasText(email)
                    && !Objects.equals(email.toLowerCase(), user.getSpec().getEmail())) {
                user.getSpec().setEmail(email.toLowerCase());
                user.getSpec().setEmailVerified(true);
                changedFields.add("email");
            }
            if (changedFields.isEmpty()) {
                return Mono.just(user);
            }
            return client.update(user).doOnNext(updated -> publishUpdated(updated, changedFields));
        });
    }

    private void publishUpdated(User user, List<String> changedFields) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("displayName", user.getSpec().getDisplayName());
        payload.put("email", user.getSpec().getEmail());
        payload.put("changedFields", changedFields);
        payload.put("source", "feishu");
        domainEventPublisher.publish("user.updated", payload, null);
    }

    public Mono<Void> deleteConnection(String username) {
        return listConnections(username).flatMap(client::delete).then();
    }

    public static String sanitizeUsername(String providerUserId) {
        var sanitized = providerUserId.replaceAll("[^A-Za-z0-9-]", "-").toLowerCase();
        if (sanitized.length() > 40) {
            sanitized = sanitized.substring(0, 40);
        }
        return "feishu-" + sanitized;
    }

    
    public record FeishuUserData(
            String username,
            String displayName,
            String email,
            String openId,
            String unionId,
            String avatar) {

        public static FeishuUserData of(
                String username, String displayName, String email, String openId, String unionId) {
            return new FeishuUserData(username, displayName, email, openId, unionId, null);
        }

        public String providerUserId() {
            return StringUtils.hasText(unionId) ? unionId : openId;
        }
    }
}
