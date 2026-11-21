package cn.tolim.pudans.core.user;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;

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
import reactor.core.scheduler.Schedulers;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.router.selector.FieldSelector;
import cn.tolim.pudans.infra.contract.ValidationUtils;
import cn.tolim.pudans.security.contract.device.DeviceSessions;
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

    // 替换时钟便于测试账号逻辑
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // 读取用户并转换底层缺失错误
    @Override
    public Mono<User> getUser(String username) {
        return client.get(User.class, username)
                .onErrorMap(ExtensionNotFoundException.class, e -> new UserNotFoundException(username));
    }

    // 按标识查询用户，空值返回空结果
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

    // 只返回邮箱已验证且匹配的用户
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

    // 仅保存已加密密码，用于登录时的哈希升级，不触发凭据吊销
    @Override
    public Mono<User> updatePassword(String username, String newPassword) {
        return getUser(username)
                .filter(user -> !Objects.equals(user.getSpec().getPassword(), newPassword))
                .flatMap(user -> {
                    user.getSpec().setPassword(newPassword);
                    return client.update(user);
                });
    }

    // 校验原始密码后加密并更新
    @Override
    public Mono<User> updateWithRawPassword(String username, String rawPassword) {
        return updateWithRawPasswordInternal(username, rawPassword);
    }

    // 统一校验长度与字符并避免新旧密码相同
    private Mono<User> updateWithRawPasswordInternal(String username, String rawPassword) {
        if (!ValidationUtils.isPasswordLengthValid(rawPassword)) {
            return Mono.error(new UnsatisfiedAttributeValueException("密码长度必须在 8 到 257 之间"));
        }
        if (!ValidationUtils.PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            return Mono.error(new UnsatisfiedAttributeValueException(
                    "密码只能使用大小写字母 (A-Z, a-z)、数字 (0-9)，以及以下特殊字符: !@#$%^&*.?"));
        }
        return getUser(username)
                .flatMap(user -> Mono.fromCallable(() -> {
                            if (StringUtils.hasText(user.getSpec().getPassword())
                                    && passwordEncoder.matches(rawPassword, user.getSpec().getPassword())) {
                                throw new UnsatisfiedAttributeValueException("新密码不能与旧密码相同。");
                            }
                            return passwordEncoder.encode(rawPassword);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(encoded -> {
                            user.getSpec().setPassword(encoded);
                            return client.update(user);
                        }))
                .doOnNext(user -> publishPasswordChangedEvent(username));
    }

    // 根据当前安全上下文判断角色是否足够
    @Override
    public Mono<Boolean> hasSufficientRoles(Collection<String> roles) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(a -> AuthorityUtils.authoritiesToRoles(a.getAuthorities()))
                .flatMap(userRoles -> roleService.contains(userRoles, roles))
                .defaultIfEmpty(false);
    }

    // 先检查名称占用再创建用户
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

    // 未设置密码时放行，否则校验原始密码
    @Override
    public Mono<Boolean> confirmPassword(String username, String rawPassword) {
        return getUser(username)
                .flatMap(user -> {
                    if (!StringUtils.hasText(user.getSpec().getPassword())) {
                        return Mono.just(true);
                    }
                    if (!StringUtils.hasText(rawPassword)) {
                        return Mono.just(false);
                    }
                    return Mono.fromCallable(
                                    () -> passwordEncoder.matches(rawPassword, user.getSpec().getPassword()))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .defaultIfEmpty(false);
    }

    // 按小写邮箱列出全部匹配用户
    @Override
    public Flux<User> listByEmail(String email) {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(equal("spec.email", email.toLowerCase())));
        return client.listAll(User.class, listOptions, defaultSort());
    }

    // 判断邮箱是否已被验证账号占用
    @Override
    public Mono<Boolean> checkEmailAlreadyVerified(String email) {
        return listByEmail(email)
                .filter(u -> u.getSpec().isEmailVerified())
                .hasElements();
    }

    // 使用配置的编码器加密密码
    @Override
    public String encryptPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    // 事务内禁用用户并撤销全部会话
    @Override
    public Mono<User> disable(String username) {
        var tx = TransactionalOperator.create(transactionManager);
        return client.fetch(User.class, username)
                .filter(user -> !Boolean.TRUE.equals(user.getSpec().getDisabled()))
                .flatMap(user -> revokeSessions(username).thenReturn(user))
                .doOnNext(user -> user.getSpec().setDisabled(true))
                .flatMap(client::update)
                .as(tx::transactional)
                .doOnNext(user -> {
                    publishDisabled(user);
                    // 复用凭据吊销流程，撤销刷新令牌、记住我令牌和个人访问令牌。
                    eventPublisher.publishEvent(new PasswordChangedEvent(this, username));
                });
    }

    // 同时撤销设备会话和注册表会话
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

    // 仅对已禁用用户重新启用
    @Override
    public Mono<User> enable(String username) {
        return client.fetch(User.class, username)
                .filter(user -> Boolean.TRUE.equals(user.getSpec().getDisabled()))
                .doOnNext(user -> user.getSpec().setDisabled(false))
                .flatMap(client::update)
                .doOnNext(this::publishEnabled);
    }

    // 发布用户被禁用事件
    private void publishDisabled(User user) {
        publishAccountState(user, "user.disabled");
    }

    // 发布用户被启用事件
    private void publishEnabled(User user) {
        publishAccountState(user, "user.enabled");
    }

    // 统一组装并发布账号状态事件
    private void publishAccountState(User user, String type) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("disabled", Boolean.TRUE.equals(user.getSpec().getDisabled()));
        domainEventPublisher.publish(type, payload, null);
    }

    // 发布密码变更事件供下游处理
    void publishPasswordChangedEvent(String username) {
        eventPublisher.publishEvent(new PasswordChangedEvent(this, username));
    }
}
