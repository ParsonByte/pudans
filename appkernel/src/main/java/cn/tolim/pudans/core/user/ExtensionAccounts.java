package cn.tolim.pudans.core.user;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.ReactiveSessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.extension.api.router.selector.FieldSelector;
import cn.tolim.pudans.infra.api.ValidationUtils;
import cn.tolim.pudans.security.api.device.DeviceSessions;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.event.user.PasswordChangedEvent;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.infra.exception.UserNotFoundException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionAccounts implements Accounts {

    private final ReactiveExtensionClient client;

    private final PasswordEncoder passwordEncoder;

    private final ApplicationEventPublisher eventPublisher;

    private final Roles roleService;

    private final DeviceSessions deviceSessions;

    private final ReactiveTransactionManager transactionManager;

    private final ReactiveSessionRegistry sessionRegistry;

    private final DomainEventPublisher domainEventPublisher;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<User> getUser(String username) {
        return client.get(User.class, username)
                .onErrorMap(ExtensionNotFoundException.class, e -> new UserNotFoundException(username));
    }

    @Override
    public Mono<User> getUserByUid(String uid) {
        if (!StringUtils.hasText(uid)) {
            return Mono.empty();
        }
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.uid", uid))
                .build();
        return client.listAll(User.class, listOptions, defaultSort()).next();
    }

    @Override
    public Mono<User> findUserByVerifiedEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Mono.empty();
        }
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.emailVerified", true))
                .andQuery(equal("spec.email", email.toLowerCase()))
                .build();
        return client.listAll(User.class, listOptions, defaultSort()).next();
    }

    @Override
    public Mono<User> updatePassword(String username, String newPassword) {
        return getUser(username)
                .filter(user -> !Objects.equals(user.getSpec().getPassword(), newPassword))
                .flatMap(user -> {
                    user.getSpec().setPassword(newPassword);
                    return client.update(user);
                })
                .doOnNext(user -> publishPasswordChangedEvent(username));
    }

    @Override
    public Mono<User> updateWithRawPassword(String username, String rawPassword) {
        return updateWithRawPasswordInternal(username, rawPassword);
    }

    private Mono<User> updateWithRawPasswordInternal(String username, String rawPassword) {
        if (!ValidationUtils.isPasswordLengthValid(rawPassword)) {
            return Mono.error(new UnsatisfiedAttributeValueException("密码长度必须在 8 到 257 之间"));
        }
        if (!ValidationUtils.PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            return Mono.error(new UnsatisfiedAttributeValueException(
                    "密码只能使用大小写字母 (A-Z, a-z)、数字 (0-9)，以及以下特殊字符: !@#$%^&*.?"));
        }
        return getUser(username)
                .flatMap(user -> {
                    if (StringUtils.hasText(user.getSpec().getPassword())
                            && passwordEncoder.matches(rawPassword, user.getSpec().getPassword())) {
                        return Mono.error(new UnsatisfiedAttributeValueException("新密码不能与旧密码相同。"));
                    }
                    user.getSpec().setPassword(passwordEncoder.encode(rawPassword));
                    return client.update(user);
                })
                .doOnNext(user -> publishPasswordChangedEvent(username));
    }

    @Override
    public Mono<Boolean> hasSufficientRoles(Collection<String> roles) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(a -> AuthorityUtils.authoritiesToRoles(a.getAuthorities()))
                .flatMap(userRoles -> roleService.contains(userRoles, roles))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<User> createUser(User user) {
        Assert.notNull(user, "用户不能为 null");
        return client.fetch(User.class, user.getMetadata().getName())
                .hasElement()
                .flatMap(hasUser -> {
                    if (hasUser) {
                        return Mono.error(new DuplicateNameException(
                                "用户名 " + user.getMetadata().getName() + " 已存在，请更换用户名后重试。", null));
                    }
                    return client.create(user);
                });
    }

    @Override
    public Mono<Boolean> confirmPassword(String username, String rawPassword) {
        return getUser(username)
                .filter(user -> {
                    if (!StringUtils.hasText(user.getSpec().getPassword())) {
                        return true;
                    }
                    if (!StringUtils.hasText(rawPassword)) {
                        return false;
                    }
                    return passwordEncoder.matches(rawPassword, user.getSpec().getPassword());
                })
                .hasElement();
    }

    @Override
    public Flux<User> listByEmail(String email) {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(equal("spec.email", email.toLowerCase())));
        return client.listAll(User.class, listOptions, defaultSort());
    }

    @Override
    public Mono<Boolean> checkEmailAlreadyVerified(String email) {
        return listByEmail(email)
                .filter(u -> u.getSpec().isEmailVerified())
                .hasElements();
    }

    @Override
    public String encryptPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public Mono<User> disable(String username) {
        var tx = TransactionalOperator.create(transactionManager);
        return client.fetch(User.class, username)
                .filter(user -> !Boolean.TRUE.equals(user.getSpec().getDisabled()))
                .flatMap(user -> revokeSessions(username).thenReturn(user))
                .doOnNext(user -> user.getSpec().setDisabled(true))
                .flatMap(client::update)
                .as(tx::transactional)
                .doOnNext(this::publishDisabled);
    }

    private Mono<Void> revokeSessions(String username) {
        var revokeDevices = deviceSessions.revoke(username);
        var invalidateSessions = sessionRegistry
                .getAllSessions(username)
                .flatMap(reactiveSessionInformation -> {
                    log.info(
                            "正在失效会话 {}（用户 {}）", reactiveSessionInformation.getSessionId(), username);
                    return reactiveSessionInformation.invalidate();
                })
                .then();
        return revokeDevices.then(invalidateSessions);
    }

    @Override
    public Mono<User> enable(String username) {
        return client.fetch(User.class, username)
                .filter(user -> Boolean.TRUE.equals(user.getSpec().getDisabled()))
                .doOnNext(user -> user.getSpec().setDisabled(false))
                .flatMap(client::update)
                .doOnNext(this::publishEnabled);
    }

    private void publishDisabled(User user) {
        publishAccountState(user, "user.disabled");
    }

    private void publishEnabled(User user) {
        publishAccountState(user, "user.enabled");
    }

    private void publishAccountState(User user, String type) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("disabled", Boolean.TRUE.equals(user.getSpec().getDisabled()));
        domainEventPublisher.publish(type, payload, null);
    }

    void publishPasswordChangedEvent(String username) {
        eventPublisher.publishEvent(new PasswordChangedEvent(this, username));
    }
}
