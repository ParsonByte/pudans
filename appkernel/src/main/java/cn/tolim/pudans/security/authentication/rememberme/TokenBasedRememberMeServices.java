package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.security.authentication.LoginParameterRequestCache;
import cn.tolim.pudans.security.authentication.SecurityConstant;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Slf4j
@Setter
@Getter
@RequiredArgsConstructor
class TokenBasedRememberMeServices implements ServerLogoutHandler, RememberMeServices {

    public static final String DEFAULT_ALGORITHM = "SHA-256";

    private static final String DELIMITER = ":";

    protected final CookieSignatureKeyResolver cookieSignatureKeyResolver;
    protected final RememberMeCookieResolver rememberMeCookieResolver;
    private final ReactiveUserDetailsService reactiveUserDetailsService;
    private final LoginParameterRequestCache parameterRequestCache;
    private UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();
    private GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();
    private String parameterName = SecurityConstant.REMEMBER_ME_PARAMETER_NAME;

    // 使用常量时间比较，避免签名校验泄露时序信息。
    private static boolean equals(String expected, String actual) {
        byte[] expectedBytes = bytesUtf8(expected);
        byte[] actualBytes = bytesUtf8(actual);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    // 空值保持为空，其余按 UTF-8 编码用于比较。
    private static byte[] bytesUtf8(String s) {
        return (s != null) ? Utf8.encode(s) : null;
    }

    // 允许外部覆盖记住我参数名，默认沿用统一常量。
    public void setParameterName(String parameterName) {
        Assert.hasText(parameterName, "参数名不能为空");
        this.parameterName = parameterName;
    }

    // 解析 Cookie 并自动登录，认证失败时统一清理。
    @Override
    public Mono<Authentication> autoLogin(ServerWebExchange exchange) {
        var rememberMeCookie = rememberMeCookieResolver.resolveRememberMeCookie(exchange);
        if (rememberMeCookie == null) {
            return Mono.empty();
        }
        log.debug("检测到记住我 Cookie");
        return Mono.defer(() -> {
                    String[] cookieTokens = decodeCookie(rememberMeCookie.getValue());
                    return processAutoLoginCookie(cookieTokens, exchange);
                })
                .flatMap(user -> {
                    this.userDetailsChecker.check(user);
                    log.debug("已接受记住我 Cookie");
                    return createSuccessfulAuthentication(exchange, user);
                })
                .onErrorResume(ex -> handleError(exchange, ex));
    }

    // 统一清理失效 Cookie，仅盗用异常继续上抛。
    private Mono<Authentication> handleError(ServerWebExchange exchange, Throwable ex) {
        cancelCookie(exchange);
        if (ex instanceof CookieTheftException) {
            log.error("检测到 Cookie 盗用", ex);
            return Mono.error(ex);
        } else if (ex instanceof UsernameNotFoundException) {
            log.debug("记住我登录有效，但未找到对应用户。", ex);
        } else if (ex instanceof InvalidCookieException) {
            log.debug("无效的记住我 Cookie：{}", ex.getMessage());
        } else if (ex instanceof AccountStatusException) {
            log.debug("无效的用户详情：{}", ex.getMessage());
        } else if (ex instanceof RememberMeAuthenticationException) {
            log.debug("记住我登录失败：{}", ex.getMessage());
        }
        return Mono.empty();
    }

    // 让浏览器立即删除记住我 Cookie。
    protected void cancelCookie(ServerWebExchange exchange) {
        rememberMeCookieResolver.expireCookie(exchange);
    }

    // 校验令牌长度、过期时间与签名，防止伪造 Cookie。
    protected Mono<UserDetails> processAutoLoginCookie(String[] cookieTokens, ServerWebExchange exchange) {
        if (!isValidCookieTokensLength(cookieTokens)) {
            throw new InvalidCookieException(
                    "Cookie 令牌应包含 3 或 4 个令牌，实际包含 '" + Arrays.asList(cookieTokens) + "'");
        }

        long tokenExpiryTime = getTokenExpiryTime(cookieTokens);
        if (isTokenExpired(tokenExpiryTime)) {
            throw new InvalidCookieException("Cookie 令牌[1] 已过期（过期时间 '" + new Date(tokenExpiryTime)
                    + "'；当前时间 '" + new Date() + "'）");
        }


        return getReactiveUserDetailsService()
                .findByUsername(cookieTokens[0])
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("未找到用户 '" + cookieTokens[0] + "'")))
                .flatMap(userDetails -> {


                    String actualTokenSignature;
                    String actualAlgorithm = DEFAULT_ALGORITHM;


                    if (cookieTokens.length == 4) {
                        actualTokenSignature = cookieTokens[3];
                        actualAlgorithm = cookieTokens[2];
                    } else {
                        actualTokenSignature = cookieTokens[2];
                    }
                    return makeTokenSignature(
                            tokenExpiryTime,
                            userDetails.getUsername(),
                            userDetails.getPassword(),
                            actualAlgorithm)
                            .doOnNext(expectedTokenSignature -> {
                                if (!equals(expectedTokenSignature, actualTokenSignature)) {
                                    throw new InvalidCookieException(
                                            "Cookie 中的签名 '" + actualTokenSignature
                                                    + "' 与预期签名 '"
                                                    + expectedTokenSignature + "'");
                                }
                            })
                            .thenReturn(userDetails);
                });
    }

    // 过期时间早于当前时间即视为失效。
    protected boolean isTokenExpired(long tokenExpiryTime) {
        return tokenExpiryTime < System.currentTimeMillis();
    }

    // 时间戳非法时转换为无效 Cookie 异常。
    private long getTokenExpiryTime(String[] cookieTokens) {
        try {
            return Long.parseLong(cookieTokens[1]);
        } catch (NumberFormatException nfe) {
            throw new InvalidCookieException(
                    "Cookie 令牌[1] 不是有效数字（实际内容为 '" + cookieTokens[1] + "'）");
        }
    }

    // 使用当前签名密钥构造记住我认证令牌。
    protected Mono<Authentication> createSuccessfulAuthentication(ServerWebExchange exchange, UserDetails user) {
        return getKey().map(key -> new RememberMeAuthenticationToken(
                key, user, this.authoritiesMapper.mapAuthorities(user.getAuthorities())));
    }

    // 兼容带算法字段与不带算法字段两种 Cookie 格式。
    private boolean isValidCookieTokensLength(String[] cookieTokens) {
        return cookieTokens.length == 3 || cookieTokens.length == 4;
    }

    // 登录失败时清除旧 Cookie 并暂存请求参数。
    @Override
    public Mono<Void> loginFail(ServerWebExchange exchange) {
        log.debug("交互式登录尝试未成功。");
        cancelCookie(exchange);
        return parameterRequestCache.saveParameter(exchange, parameterName);
    }

    // 仅在用户勾选记住我时签发 Cookie，并清理暂存参数。
    @Override
    public Mono<Void> loginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication) {
        return rememberMeRequested(exchange)
                .filter(Boolean::booleanValue)
                .flatMap(rememberMeRequest -> onLoginSuccess(exchange, successfulAuthentication))
                .then(parameterRequestCache.removeParameter(exchange, parameterName));
    }

    // 基于用户名与密码生成签名 Cookie，作为默认实现。
    protected Mono<Void> onLoginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication) {
        return Mono.defer(() -> retrieveUsernamePassword(successfulAuthentication))
                .flatMap(pair -> {
                    var username = pair.username();
                    var password = pair.password();
                    var expiryTimeMs = calculateExpireTime(exchange, successfulAuthentication);
                    return makeTokenSignature(expiryTimeMs, username, password, DEFAULT_ALGORITHM)
                            .doOnNext(signatureValue -> {
                                setCookie(
                                        new String[] {
                                                username, Long.toString(expiryTimeMs), DEFAULT_ALGORITHM, signatureValue
                                        },
                                        exchange);
                                if (log.isDebugEnabled()) {
                                    log.debug(
                                            "已为用户 '{}' 添加记住我 Cookie，过期时间：'{}'",
                                            username,
                                            new Date(expiryTimeMs));
                                }
                            });
                })
                .then();
    }

    // 依次检查查询参数、表单与暂存参数中的记住我标记。
    private Mono<Boolean> rememberMeRequested(ServerWebExchange exchange) {

        return Mono.justOrEmpty(exchange.getRequest().getQueryParams().getFirst(parameterName))
                .filter(Predicate.not(String::isBlank))

                .switchIfEmpty(Mono.defer(() -> exchange.getFormData()
                        .mapNotNull(f -> f.getFirst(parameterName))
                        .filter(Predicate.not(String::isBlank))))

                .switchIfEmpty(Mono.defer(() -> parameterRequestCache.getParameter(exchange, parameterName)))
                .map(Boolean::parseBoolean)
                .defaultIfEmpty(false);
    }

    // 密码缺失时回查用户详情，保证签名输入完整。
    private Mono<UsernamePassword> retrieveUsernamePassword(Authentication successfulAuthentication) {
        return Mono.defer(() -> {
            String username = retrieveUserName(successfulAuthentication);
            String password = retrievePassword(successfulAuthentication);


            if (!StringUtils.hasLength(username)) {
                log.debug("无法获取用户名");
                return Mono.empty();
            }
            if (!StringUtils.hasLength(password)) {
                return getReactiveUserDetailsService().findByUsername(username).flatMap(user -> {
                    String existingPassword = user.getPassword();
                    if (!StringUtils.hasLength(existingPassword)) {
                        log.debug("无法获取用户 {} 的密码", username);
                        return Mono.empty();
                    }
                    return Mono.just(new UsernamePassword(username, existingPassword));
                });
            }
            return Mono.just(new UsernamePassword(username, password));
        });
    }

    // 编码令牌后交给 Cookie 解析器写入响应。
    void setCookie(String[] cookieTokens, ServerWebExchange exchange) {
        String cookieValue = encodeCookie(cookieTokens);
        rememberMeCookieResolver.setRememberMeCookie(exchange, cookieValue);
    }

    // 以 Cookie 有效期计算绝对过期时间戳。
    protected long calculateExpireTime(ServerWebExchange exchange, Authentication authentication) {
        var tokenLifetime = rememberMeCookieResolver.getCookieMaxAge().toSeconds();
        return Instant.now().plusSeconds(tokenLifetime).toEpochMilli();
    }

    // 补全 Base64 填充后解码并还原各令牌字段。
    protected String[] decodeCookie(String cookieValue) throws InvalidCookieException {
        int paddingCount = 4 - (cookieValue.length() % 4);
        if (paddingCount < 4) {
            char[] padding = new char[paddingCount];
            Arrays.fill(padding, '=');
            cookieValue += new String(padding);
        }
        String cookieAsPlainText;
        try {
            cookieAsPlainText = new String(Base64.getDecoder().decode(cookieValue.getBytes()));
        } catch (IllegalArgumentException ex) {
            throw new InvalidCookieException("Cookie 令牌不是 Base64 编码；值为 '" + cookieValue + "'");
        }
        String[] tokens = StringUtils.delimitedListToStringArray(cookieAsPlainText, DELIMITER);
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = URLDecoder.decode(tokens[i], StandardCharsets.UTF_8);
        }
        return tokens;
    }


    // 逐段 URL 编码后 Base64 编码，并去掉末尾填充。
    protected String encodeCookie(String[] cookieTokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cookieTokens.length; i++) {
            sb.append(URLEncoder.encode(cookieTokens[i], StandardCharsets.UTF_8));
            if (i < cookieTokens.length - 1) {
                sb.append(DELIMITER);
            }
        }
        String value = sb.toString();
        sb = new StringBuilder(new String(Base64.getEncoder().encode(value.getBytes())));
        while (sb.charAt(sb.length() - 1) == '=') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    // 使用用户名、过期时间、密码与密钥生成摘要签名。
    protected Mono<String> makeTokenSignature(
            long tokenExpiryTime, String username, String password, String algorithm) {
        return getKey().flatMap(key -> Mono.fromCallable(() -> {
                    var data = username + ":" + tokenExpiryTime + ":" + password + ":" + key;
                    var digest = MessageDigest.getInstance(algorithm);
                    return new String(Hex.encode(digest.digest(data.getBytes())));
                }))
                .onErrorMap(
                        NoSuchAlgorithmException.class,
                        ex -> new IllegalStateException("没有可用的 " + algorithm + " 算法！", ex));
    }

    // 优先取用户详情用户名，兼容其他认证主体。
    protected String retrieveUserName(Authentication authentication) {
        var principal = authentication.getPrincipal();
        Assert.notNull(principal, "认证主体不能为空");
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal.toString();
    }

    // 优先读取用户详情密码，其次读取认证凭据。
    @Nullable
    protected String retrievePassword(Authentication authentication) {
        var principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getPassword();
        }
        if (authentication.getCredentials() != null) {
            return authentication.getCredentials().toString();
        }
        return null;
    }

    // 获取当前签名密钥，供 Cookie 签名与校验使用。
    protected Mono<String> getKey() {
        return cookieSignatureKeyResolver.resolveSigningKey();
    }

    // 退出登录时复用登录失败逻辑，确保 Cookie 与令牌清理。
    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        if (log.isDebugEnabled()) {
            log.debug("用户 {} 退出登录", (authentication != null) ? authentication.getName() : "未知");
        }
        return loginFail(exchange.getExchange()).then(onLogout(exchange, authentication));
    }

    // 默认无额外退出清理，由子类覆盖扩展。
    protected Mono<Void> onLogout(WebFilterExchange exchange, Authentication authentication) {
        return Mono.empty();
    }

    record UsernamePassword(String username, String password) {}
}
