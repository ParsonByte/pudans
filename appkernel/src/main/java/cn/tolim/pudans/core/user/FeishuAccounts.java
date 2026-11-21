package cn.tolim.pudans.core.user;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.contract.index.query.Queries.in;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.setting.ParameterGroups;

@Service
@RequiredArgsConstructor
public class FeishuAccounts {

    public static final String PROVIDER = "feishu";

    private final ReactiveExtensionClient client;

    private final Accounts accounts;

    private final Connections connections;

    private final Groups groups;

    private final DomainEventPublisher domainEventPublisher;

    private final ReactiveTransactionManager transactionManager;

    private final ParameterGroups parameterGroups;

    private Clock clock = Clock.systemUTC();

    // 先找到飞书连接再返回绑定用户
    public Mono<User> findByProviderUserId(String providerUserId) {
        return findConnection(providerUserId).flatMap(connection -> accounts.getUser(
                connection.getSpec().getUsername()));
    }

    // 按飞书用户标识查找未删除连接
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

    // 列出全部未删除的飞书连接
    public Flux<UserConnection> listAllConnections() {
        var options = ListOptions.builder().andQuery(notDeleting()).build();
        return client.listAll(UserConnection.class, options, defaultSort())
                .filter(connection -> PROVIDER.equals(connection.getSpec().getRegistrationId()));
    }

    // 列出指定用户的飞书连接
    public Flux<UserConnection> listConnections(String username) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("spec.username", username))
                .build();
        return client.listAll(UserConnection.class, options, defaultSort())
                .filter(connection -> PROVIDER.equals(connection.getSpec().getRegistrationId()));
    }

    

    // 校验资料后创建用户、连接与用户组
    public Mono<User> createFromFeishu(FeishuUserData data, String userGroupName) {
        if (data == null || (!StringUtils.hasText(data.unionId()) && !StringUtils.hasText(data.openId()))) {
            return Mono.error(new ServerWebInputException(
                    "创建本地用户需要飞书的 union_id 或 open_id。"));
        }
        if (!StringUtils.hasText(data.displayName()) || !StringUtils.hasText(data.email())) {
            return Mono.error(new ServerWebInputException(
                    "displayName 和 email 为必填项，且必须来自飞书。"));
        }
        var providerUserId =
                StringUtils.hasText(data.unionId()) ? data.unionId() : data.openId();
        return findConnection(providerUserId)
                .flatMap(existing -> Mono.<User>error(new ServerWebInputException(
                        "飞书身份 [" + providerUserId + "] 已绑定到用户 ["
                                + existing.getSpec().getUsername() + "]。")))
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
                    return accounts.createUser(user);
                }))
                .flatMap(user -> createConnection(user, providerUserId, data.displayName()).thenReturn(user))
                .flatMap(user -> StringUtils.hasText(userGroupName)
                        ? groups.assignUserGroup(user.getMetadata().getName(), userGroupName)
                                .thenReturn(user)
                        : assignDefaultUserGroup(user.getMetadata().getName()).thenReturn(user))
                .as(TransactionalOperator.create(transactionManager)::transactional)
                .doOnNext(user -> domainEventPublisher.publish("user.created", UserEventPayloads.created(user), null));
    }

    // 使用默认用户组创建飞书用户
    public Mono<User> createFromFeishu(FeishuUserData data) {
        return createFromFeishu(data, null);
    }

    // 分配默认组，失败时回退内置组
    Mono<Void> assignDefaultUserGroup(String username) {
        return parameterGroups
                .getSession()
                .map(session -> StringUtils.hasText(session.getDefaultUserGroup())
                        ? session.getDefaultUserGroup()
                        : BuiltinEntities.NORMAL_USERS_GROUP)
                .defaultIfEmpty(BuiltinEntities.NORMAL_USERS_GROUP)
                .onErrorResume(error -> Mono.just(BuiltinEntities.NORMAL_USERS_GROUP))
                .flatMap(groupName -> client.fetch(UserGroup.class, groupName)
                        .switchIfEmpty(Mono.defer(
                                () -> client.fetch(UserGroup.class, BuiltinEntities.NORMAL_USERS_GROUP))))
                .flatMap(group -> groups.assignUserGroup(username, group.getMetadata().getName()))
                .then();
    }

    // 构造并保存飞书身份连接
    private Mono<UserConnection> createConnection(User user, String providerUserId, String displayName) {
        var connection = new UserConnection();
        var spec = new UserConnection.UserConnectionSpec();
        spec.setRegistrationId(PROVIDER);
        spec.setUsername(user.getMetadata().getName());
        spec.setProviderUserId(providerUserId);
        spec.setDisplayName(displayName);
        connection.setSpec(spec);
        return connections.createConnection(connection);
    }

    // 校验身份归属后绑定并同步资料
    public Mono<UserConnection> bindConnection(
            String username,
            String providerUserId,
            String displayName,
            String email,
            String phone,
            String avatar) {
        if (!StringUtils.hasText(providerUserId)) {
            return Mono.error(new ServerWebInputException("飞书身份缺少 union_id 或 open_id。"));
        }
        return accounts
                .getUser(username)
                .switchIfEmpty(Mono.error(new ServerWebInputException("当前用户不存在。")))
                .flatMap(user -> findConnection(providerUserId)
                        .flatMap(existing -> Objects.equals(
                                        existing.getSpec().getUsername(), username)
                                ? Mono.just(existing)
                                : Mono.error(new ServerWebInputException(
                                        "该飞书身份已绑定到其他用户。")))
                        .switchIfEmpty(Mono.defer(() -> createConnection(user, providerUserId, displayName))))
                .flatMap(connection ->
                        syncProfile(username, displayName, email, phone, avatar).then(Mono.just(connection)));
    }



    // 仅更新有变化的资料字段并发布事件
    public Mono<User> syncProfile(
            String username, String displayName, String email, String phone, String avatar) {
        if (!StringUtils.hasText(displayName)
                && !StringUtils.hasText(email)
                && !StringUtils.hasText(phone)
                && !StringUtils.hasText(avatar)) {
            return Mono.empty();
        }
        return accounts.getUser(username).flatMap(user -> {
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
            if (StringUtils.hasText(phone) && !Objects.equals(phone, user.getSpec().getPhone())) {
                user.getSpec().setPhone(phone);
                changedFields.add("phone");
            }
            if (StringUtils.hasText(avatar) && !Objects.equals(avatar, user.getSpec().getAvatar())) {
                user.getSpec().setAvatar(avatar);
                changedFields.add("avatar");
            }
            if (changedFields.isEmpty()) {
                return Mono.just(user);
            }
            return client.update(user).doOnNext(updated -> publishUpdated(updated, changedFields));
        });
    }

    // 发布来源为飞书的用户更新事件
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

    // 将飞书标识转换为合法本地用户名
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

        // 构造不含头像的飞书用户资料
        public static FeishuUserData of(
                String username, String displayName, String email, String openId, String unionId) {
            return new FeishuUserData(username, displayName, email, openId, unionId, null);
        }

        // 优先使用联合标识，缺失时回退开放标识
        public String providerUserId() {
            return StringUtils.hasText(unionId) ? unionId : openId;
        }
    }
}
