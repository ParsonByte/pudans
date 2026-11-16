package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.contract.core.extension.RememberMeToken;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.security.device.DeviceService;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.security.authentication.LoginParameterRequestCache;
import cn.tolim.pudans.security.authentication.SecurityConstant;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Slf4j
@Setter
@Component
public class PersistentTokenBasedRememberMeServices extends TokenBasedRememberMeServices implements RememberMeServices {

    public static final String REMEMBER_ME_SERIES_REQUEST_NAME = "remember-me-series";

    public static final int DEFAULT_SERIES_LENGTH = 16;

    public static final int DEFAULT_TOKEN_LENGTH = 16;

    private final SecureRandom random;

    private final int seriesLength = DEFAULT_SERIES_LENGTH;

    private final int tokenLength = DEFAULT_TOKEN_LENGTH;

    private final PersistentRememberMeTokenRepository tokenRepository;

    private final DeviceService deviceService;

    private final Duration rotationCooldown;

    private Clock clock = Clock.systemUTC();

    public PersistentTokenBasedRememberMeServices(
            CookieSignatureKeyResolver cookieSignatureKeyResolver,
            ReactiveUserDetailsService userDetailsService,
            RememberMeCookieResolver rememberMeCookieResolver,
            PersistentRememberMeTokenRepository tokenRepository,
            LoginParameterRequestCache parameterRequestCache,
            DeviceService deviceService,
            PudansProperties pudansProperties) {
        super(cookieSignatureKeyResolver, rememberMeCookieResolver, userDetailsService, parameterRequestCache);
        this.random = new SecureRandom();
        this.tokenRepository = tokenRepository;
        this.deviceService = deviceService;
        this.rotationCooldown = pudansProperties.getSecurity().getRememberMe().getRotationCooldown();
        setParameterName(SecurityConstant.REMEMBER_ME_PARAMETER_NAME);
    }

    @Override
    protected Mono<UserDetails> processAutoLoginCookie(String[] cookieTokens, ServerWebExchange exchange) {
        if (cookieTokens.length != 2) {
            return Mono.error(new InvalidCookieException("Cookie 令牌应包含 %d 个令牌，实际包含 '%s'"
                    .formatted(2, Arrays.asList(cookieTokens))));
        }
        var presentedSeries = cookieTokens[0];
        var presentedToken = cookieTokens[1];
        log.debug("正在处理序列 '{}' 的记住我自动登录", presentedSeries);
        return this.tokenRepository
                .getTokenForSeries(presentedSeries)
                .switchIfEmpty(Mono.error(() -> {
                    log.debug("未找到序列 '{}' 的记住我令牌", presentedSeries);
                    return new RememberMeAuthenticationException(
                            "未找到持久化令牌，序列 ID：" + presentedSeries);
                }))
                .doOnNext(token -> log.debug(
                        "找到用户 '{}' 的记住我令牌，序列 '{}'，lastUsed={}，tokenMatch={}",
                        token.getSpec().getUsername(),
                        token.getSpec().getSeries(),
                        token.getSpec().getLastUsed(),
                        Objects.equals(token.getSpec().getTokenValue(), presentedToken)))
                .delayUntil(token -> validateDevice(exchange, token))
                .delayUntil(token -> {
                    if (!Objects.equals(token.getSpec().getTokenValue(), presentedToken)) {
                        if (isTokenStolen(token, presentedToken)) {
                            log.warn(
                                    "检测到用户 '{}' 的 Cookie 盗用，序列 '{}'："
                                            + "presentedToken 与存储的令牌不匹配，"
                                            + "且超出冷却时间或与上一个令牌不匹配。"
                                            + "正在移除该用户的所有令牌。",
                                    token.getSpec().getUsername(),
                                    token.getSpec().getSeries());
                            return this.tokenRepository
                                    .removeUserTokens(token.getSpec().getUsername())
                                    .then(Mono.error(() -> new CookieTheftException("""
                                            记住我令牌无效（序列/令牌）不匹配。\
                                            这表明此前发生了 Cookie 盗用攻击。""")));
                        }
                        log.debug(
                                "已接受用户 '{}' 之前的记住我令牌，序列 '{}'",
                                token.getSpec().getUsername(),
                                token.getSpec().getSeries());
                    }
                    if (isTokenExpired(token)) {
                        log.warn(
                                "用户 '{}' 的记住我令牌已过期，序列 '{}'，lastUsed={}，正在移除该用户的所有令牌",
                                token.getSpec().getUsername(),
                                token.getSpec().getSeries(),
                                token.getSpec().getLastUsed());
                        return this.tokenRepository
                                .removeUserTokens(token.getSpec().getUsername())
                                .then(Mono.error(new InvalidCookieException("记住我登录已过期")));
                    }
                    return Mono.empty();
                })
                .flatMap(token -> {
                    if (!Objects.equals(token.getSpec().getTokenValue(), presentedToken)) {
                        return Mono.just(token);
                    }
                    if (isRecentlyRotated(token)) {
                        log.debug(
                                "跳过用户 '{}' 的记住我令牌轮换，序列 '{}'："
                                        + "上次轮换距今 {}，冷却时间为 {}",
                                token.getSpec().getUsername(),
                                token.getSpec().getSeries(),
                                Duration.between(token.getSpec().getLastUsed(), clock.instant()),
                                rotationCooldown);
                        return Mono.just(token);
                    }
                    log.debug(
                            "正在轮换用户 '{}' 的记住我令牌，序列 '{}'",
                            token.getSpec().getUsername(),
                            token.getSpec().getSeries());
                    token.getSpec().setPreviousTokenValue(presentedToken);
                    token.getSpec().setTokenValue(generateTokenData());
                    token.getSpec().setLastUsed(clock.instant());
                    return tokenRepository
                            .updateToken(token)
                            .doOnNext(updated -> log.debug(
                                    "已成功轮换用户 '{}' 的记住我令牌，序列 '{}'",
                                    updated.getSpec().getUsername(),
                                    updated.getSpec().getSeries()))
                            .onErrorResume(OptimisticLockingFailureException.class, e -> {
                                log.warn(
                                        "轮换用户 '{}' 的令牌（序列 '{}'）时发生乐观锁冲突，"
                                                + "回退到使用新令牌",
                                        token.getSpec().getUsername(),
                                        token.getSpec().getSeries());
                                return tokenRepository
                                        .getTokenForSeries(presentedSeries)
                                        .defaultIfEmpty(token);
                            });
                })
                .doOnNext(token -> addCookie(token, exchange))
                .flatMap(t -> getUserDetailsService().findByUsername(t.getSpec().getUsername()));
    }

    private Mono<Void> validateDevice(ServerWebExchange exchange, RememberMeToken token) {
        return deviceService
                .resolveCurrentDevice(exchange)
                .switchIfEmpty(Mono.error(() -> {
                    log.debug(
                            "用户 '{}' 的记住我设备校验失败，序列 '{}'："
                                    + "未找到设备 Cookie",
                            token.getSpec().getUsername(),
                            token.getSpec().getSeries());
                    return new RememberMeAuthenticationException(
                            "无法确定用于记住我认证的设备");
                }))
                .filter(d -> Objects.equals(
                        d.getSpec().getRememberMeSeriesId(), token.getSpec().getSeries()))
                .switchIfEmpty(Mono.error(() -> {
                    log.warn(
                            "用户 '{}' 的记住我设备校验失败，序列 '{}'："
                                    + "设备序列 ID 与令牌序列不匹配",
                            token.getSpec().getUsername(),
                            token.getSpec().getSeries());
                    return new RememberMeAuthenticationException(
                            "记住我序列 ID 与当前设备的序列 ID 不匹配");
                }))
                .then();
    }

    private boolean isTokenStolen(RememberMeToken token, String presentedToken) {


        if (Objects.equals(presentedToken, token.getSpec().getPreviousTokenValue())) {
            var lastUsed = Optional.ofNullable(token.getSpec().getLastUsed())
                    .orElseGet(() -> token.getMetadata().getCreationTimestamp());
            return clock.instant().isAfter(lastUsed.plus(rotationCooldown));
        }


        return true;
    }

    private boolean isTokenExpired(RememberMeToken token) {
        var lastUsed = Optional.ofNullable(token.getSpec().getLastUsed())
                .orElseGet(() -> token.getMetadata().getCreationTimestamp());
        var now = clock.instant();
        return now.isAfter(lastUsed.plus(rememberMeCookieResolver.getCookieMaxAge()));
    }


    private boolean isRecentlyRotated(RememberMeToken token) {
        var lastUsed = Optional.ofNullable(token.getSpec().getLastUsed())
                .orElseGet(() -> token.getMetadata().getCreationTimestamp());
        return clock.instant().isBefore(lastUsed.plus(rotationCooldown));
    }


    @Override
    protected Mono<Void> onLoginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication) {
        var username = successfulAuthentication.getName();
        log.debug("正在为用户 '{}' 创建新的记住我持久登录", username);
        var t = new RememberMeToken();
        t.setMetadata(new Metadata());
        t.setSpec(new RememberMeToken.Spec());
        var seriesId = generateSeriesData();
        var tokenValue = generateTokenData();
        t.getMetadata().setGenerateName(username + '-');
        t.getSpec().setLastUsed(clock.instant());
        t.getSpec().setSeries(seriesId);
        t.getSpec().setTokenValue(tokenValue);
        t.getSpec().setUsername(username);
        return this.tokenRepository
                .createNewToken(t)
                .doOnNext(created -> {
                    log.debug(
                            "已为用户 '{}' 创建记住我令牌，序列 '{}'",
                            username,
                            created.getSpec().getSeries());
                    addCookie(created, exchange);
                })
                .doOnError(e -> log.error(
                        "无法为用户 '{}' 创建记住我令牌，序列 '{}'", username, seriesId, e))
                .onErrorComplete()
                .then();
    }

    @Override
    protected Mono<Void> onLogout(WebFilterExchange exchange, Authentication authentication) {
        if (authentication != null) {
            return this.tokenRepository.removeUserTokens(authentication.getName());
        }
        return Mono.empty();
    }

    private void addCookie(RememberMeToken token, ServerWebExchange exchange) {
        var spec = token.getSpec();
        setCookie(new String[] {spec.getSeries(), spec.getTokenValue()}, exchange);
        exchange.getAttributes().put(REMEMBER_ME_SERIES_REQUEST_NAME, spec.getSeries());
    }

    protected String generateSeriesData() {
        byte[] newSeries = new byte[this.seriesLength];
        this.random.nextBytes(newSeries);
        return new String(Base64.getEncoder().encode(newSeries));
    }

    protected String generateTokenData() {
        byte[] newToken = new byte[this.tokenLength];
        this.random.nextBytes(newToken);
        return new String(Base64.getEncoder().encode(newToken));
    }
}
