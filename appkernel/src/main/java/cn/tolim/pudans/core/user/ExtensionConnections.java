package cn.tolim.pudans.core.user;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.contract.user.UserConnection.UserConnectionSpec;
import cn.tolim.pudans.event.user.UserConnectionDisconnectedEvent;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.MetadataOperator;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import cn.tolim.pudans.infra.exception.IdentityAlreadyBoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ExtensionConnections implements Connections {

    private static final int RECONNECT_MAX_ATTEMPTS = 20;

    private static final Duration RECONNECT_RETRY_DELAY = Duration.ofMillis(100);

    private static final String OAUTH2_USER_INFO_ANNOTATION = "auth.pudans.tolim.cn/oauth2-user-info";

    private final ReactiveExtensionClient client;

    private final ApplicationEventPublisher eventPublisher;

    private Clock clock = Clock.systemDefaultZone();

    private JsonMapper mapper = JsonMapper.shared();

    public ExtensionConnections(ReactiveExtensionClient client, ApplicationEventPublisher eventPublisher) {
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<UserConnection> createConnection(UserConnection connection) {
        return Mono.defer(() -> {
            var spec = connection.getSpec();
            if (spec == null
                    || !StringUtils.hasText(spec.getUsername())
                    || !StringUtils.hasText(spec.getRegistrationId())
                    || !StringUtils.hasText(spec.getProviderUserId())) {
                return Mono.error(new ServerWebInputException("身份连接的用户、提供方和提供方用户标识不能为空。"));
            }
            if (connection.getMetadata() == null) {
                connection.setMetadata(new Metadata());
            }
            connection.getMetadata().setName(connectionName(spec.getRegistrationId(), spec.getProviderUserId()));
            spec.setUpdatedAt(clock.instant());
            return getByProviderUserId(spec.getRegistrationId(), spec.getProviderUserId())
                    .flatMap(existing ->
                            Mono.<UserConnection>error(() -> new IdentityAlreadyBoundException(existing)))
                    .switchIfEmpty(Mono.defer(() -> client.create(connection)
                            .onErrorResume(
                                    DuplicateNameException.class,
                                    original -> retryCreateWhileDeleting(
                                            connection, original, RECONNECT_MAX_ATTEMPTS))
                            .onErrorResume(original -> getByProviderUserId(
                                            spec.getRegistrationId(), spec.getProviderUserId())
                                    .flatMap(existing ->
                                            Mono.<UserConnection>error(new IdentityAlreadyBoundException(existing)))
                                    .switchIfEmpty(Mono.error(original)))));
        });
    }

    @Override
    public Mono<UserConnection> createOAuth2Connection(
            String username, String registrationId, OAuth2User oauth2User) {
        var connection = new UserConnection();
        var metadata = new Metadata();
        connection.setMetadata(metadata);
        updateUserInfo(metadata, oauth2User);
        var spec = new UserConnectionSpec();
        spec.setUsername(username);
        spec.setRegistrationId(registrationId);
        spec.setProviderUserId(oauth2User.getName());
        spec.setDisplayName(displayName(oauth2User));
        connection.setSpec(spec);
        return createConnection(connection);
    }

    private Mono<UserConnection> retryCreateWhileDeleting(
            UserConnection connection, DuplicateNameException original, int remainingAttempts) {
        var name = connection.getMetadata().getName();
        return Mono.defer(() -> client.fetch(UserConnection.class, name))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(existing -> {
                    if (existing.isPresent() && existing.get().getMetadata().getDeletionTimestamp() == null) {
                        return Mono.error(new IdentityAlreadyBoundException(existing.get()));
                    }
                    if (remainingAttempts == 0) {
                        return Mono.error(original);
                    }
                    return Mono.delay(RECONNECT_RETRY_DELAY)
                            .then(client.create(connection))
                            .onErrorResume(
                                    DuplicateNameException.class,
                                    ignored -> retryCreateWhileDeleting(
                                            connection, original, remainingAttempts - 1));
                });
    }

    private Mono<UserConnection> updateUserConnection(UserConnection connection, OAuth2User oauth2User) {
        connection.getSpec().setUpdatedAt(clock.instant());
        var displayName = displayName(oauth2User);
        if (StringUtils.hasText(displayName)) {
            connection.getSpec().setDisplayName(displayName);
        }
        updateUserInfo(connection.getMetadata(), oauth2User);
        return client.update(connection);
    }

    @Override
    public Mono<UserConnection> updateUserConnectionIfPresent(String registrationId, OAuth2User oauth2User) {
        return getByProviderUserId(registrationId, oauth2User.getName())
                .flatMap(connection -> updateUserConnection(connection, oauth2User));
    }

    @Override
    public Flux<UserConnection> listConnections(String username) {
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.username", username))
                .andQuery(notDeleting())
                .build();
        return client.listAll(UserConnection.class, listOptions, defaultSort());
    }

    @Override
    public Mono<UserConnection> removeConnection(String username, String connectionName) {
        return client.fetch(UserConnection.class, connectionName)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "身份连接不存在。")))
                .flatMap(connection -> {
                    if (!username.equals(connection.getSpec().getUsername())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "不能删除其他用户的连接。"));
                    }
                    return isLastSignInMethod(username, connection)
                            .flatMap(last -> Boolean.TRUE.equals(last)
                                    ? Mono.<UserConnection>error(new ServerWebInputException(
                                            "没有 Passkey 时不能移除唯一的登录方式。"))
                                    : client.delete(connection));
                })
                .doOnNext(deleted -> eventPublisher.publishEvent(
                        new UserConnectionDisconnectedEvent(this, deleted)));
    }

    private Mono<Boolean> isLastSignInMethod(String username, UserConnection target) {
        return listConnections(username)
                .filter(connection ->
                        !connection.getMetadata().getName().equals(target.getMetadata().getName()))
                .collectList()
                .flatMap(remaining -> client.fetch(User.class, username)
                        .map(user -> !remaining.isEmpty()
                                || StringUtils.hasText(user.getSpec().getPassword()))
                        .defaultIfEmpty(false))
                .map(canStay -> !canStay);
    }

    private Mono<UserConnection> getByProviderUserId(String registrationId, String providerUserId) {
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.registrationId", registrationId))
                .andQuery(equal("spec.providerUserId", providerUserId))
                .andQuery(notDeleting())
                .build();
        return client.listAll(UserConnection.class, listOptions, defaultSort()).next();
    }

    private static String connectionName(String registrationId, String providerUserId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest((registrationId + '\0' + providerUserId).getBytes(StandardCharsets.UTF_8));
            return registrationId + "-" + HexFormat.of().formatHex(digest, 0, 28);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String displayName(OAuth2User oauth2User) {
        var attributes = oauth2User.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (var key : List.of("name", "displayName", "preferred_username")) {
            var value = attributes.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private void updateUserInfo(MetadataOperator metadata, OAuth2User oauth2User) {
        var annotations = Optional.ofNullable(metadata.getAnnotations()).orElseGet(HashMap::new);
        metadata.setAnnotations(annotations);
        annotations.put(OAUTH2_USER_INFO_ANNOTATION, mapper.writeValueAsString(oauth2User.getAttributes()));
    }
}
