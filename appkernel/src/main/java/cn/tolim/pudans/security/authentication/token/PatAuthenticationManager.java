package cn.tolim.pudans.security.authentication.token;

import static cn.tolim.pudans.security.contract.PersonalAccessToken.PAT_TOKEN_PREFIX;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.ANONYMOUS_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.AUTHENTICATED_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static java.nio.charset.StandardCharsets.UTF_8;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
import cn.tolim.pudans.infra.setting.MasterKeys;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import com.nimbusds.jwt.JWTClaimNames;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.apache.commons.lang3.Strings;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class PatAuthenticationManager implements ReactiveAuthenticationManager {


    private static final Duration MIN_UPDATE_GAP = Duration.ofMinutes(1);

    private final ReactiveAuthenticationManager delegate;

    private final ReactiveExtensionClient client;

    private final Roles roleService;

    private final UserRoles userRoles;

    private final MasterKeys masterKeys;

    private Clock clock;

    // 组装认证所需的客户端、角色与主密钥依赖。
    public PatAuthenticationManager(
            ReactiveExtensionClient client,
            ReactiveAuthenticationManager delegate,
            Roles roleService,
            UserRoles userRoles,
            MasterKeys masterKeys) {
        this.client = client;
        this.delegate = delegate;
        this.roleService = roleService;
        this.userRoles = userRoles;
        this.masterKeys = masterKeys;
        this.clock = Clock.systemDefaultZone();
    }

    // 将令牌解码为名称与密钥，格式错误直接拒绝。
    private static String[] parseToken(String token) {
        final String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token), UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("无效的个人访问令牌。", e);
        }
        var separator = decoded.indexOf(':');
        if (separator <= 0 || separator == decoded.length() - 1) {
            throw new InvalidBearerTokenException("无效的个人访问令牌。");
        }
        return new String[] {decoded.substring(0, separator), decoded.substring(separator + 1)};
    }

    // 使用主密钥按密钥版本校验令牌密钥。
    private boolean secretMatches(PersonalAccessToken pat, String secret) {
        return masterKeys.matches(
                pat.getSpec().getTokenId(), secret, pat.getSpec().getKeyVersion());
    }

    // 角色模式下不附加作用域，其余转为权限。
    private static void addScopeAuthorities(PersonalAccessToken pat, List<GrantedAuthority> authorities) {
        var spec = pat.getSpec();
        if (PersonalAccessToken.SCOPE_MODE_ROLES_ONLY.equals(spec.getScopeMode())) {
            return;
        }
        var scopes = spec.getScopes();
        if (scopes == null) {
            return;
        }
        scopes.stream()
                .map(scope -> AuthorityUtils.SCOPE_PREFIX + scope)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
    }

    // 区分新旧两种个人访问令牌分别认证。
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        var sudoUsername = authentication instanceof SudoBearerTokenAuthenticationToken sudo
                ? sudo.getSudoUsername()
                : null;
        return Mono.justOrEmpty(authentication)
                .cast(BearerTokenAuthenticationToken.class)
                .map(t -> Strings.CS.removeStart(t.getToken(), PAT_TOKEN_PREFIX))
                .flatMap(token -> {
                    if (token.indexOf('.') >= 0) {
                        if (masterKeys.currentVersion() != 0) {
                            return Mono.error(new InvalidBearerTokenException(
                                    "主密钥轮换后，旧版个人访问令牌已失效。"));
                        }
                        return legacyAuthenticate(token, sudoUsername);
                    }
                    return authenticateOpaque(token, sudoUsername);
                });
    }

    // 旧版令牌走标准解析，且不允许代操作。
    private Mono<Authentication> legacyAuthenticate(String token, @Nullable String sudoUsername) {
        if (StringUtils.hasText(sudoUsername)) {
            return Mono.error(new InvalidBearerTokenException("旧版个人访问令牌不支持 sudo。"));
        }
        return delegate.authenticate(new BearerTokenAuthenticationToken(token))
                .cast(JwtAuthenticationToken.class)
                .flatMap(this::checkAndRebuild)
                .map(auth -> auth);
    }

    // 校验不透明令牌有效性后构建认证结果。
    private Mono<Authentication> authenticateOpaque(String token, @Nullable String sudoUsername) {
        return Mono.defer(() -> {
            var parts = parseToken(token);
            var patName = parts[0];
            var secret = parts[1];
            return client.fetch(PersonalAccessToken.class, patName)
                    .switchIfEmpty(Mono.error(() -> new DisabledException("个人访问令牌已被删除。")))
                    .flatMap(pat -> opaquePatChecks(pat, secret)
                            .then(checkUser(
                                    pat.getSpec().getUsername(), pat.getSpec().getRoles()))
                            .then(checkSudo(pat, sudoUsername))
                            .then(updateLastUsed(patName))
                            .thenReturn(pat))
                    .flatMap(pat -> buildAuthentication(pat, token, sudoUsername));
        });
    }

    // 依次检查删除、撤销、过期与密钥匹配状态。
    private Mono<Void> opaquePatChecks(PersonalAccessToken pat, String secret) {
        if (ExtensionUtil.isDeleted(pat)) {
            return Mono.error(new InvalidBearerTokenException("个人访问令牌正在删除中。"));
        }
        var spec = pat.getSpec();
        if (spec.isRevoked()) {
            return Mono.error(new InvalidBearerTokenException("令牌已被撤销。"));
        }
        var expiresAt = spec.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(clock.instant())) {
            return Mono.error(new InvalidBearerTokenException("令牌已过期。"));
        }
        if (!secretMatches(pat, secret)) {
            return Mono.error(
                    new InvalidBearerTokenException("令牌密钥与个人访问令牌不匹配。"));
        }
        return Mono.empty();
    }

    // 按生效角色构建携带代操作声明的认证令牌。
    private Mono<Authentication> buildAuthentication(
            PersonalAccessToken pat, String tokenValue, @Nullable String sudoUsername) {
        return effectiveRoles(pat, sudoUsername).map(roles -> {
            var authorities = new ArrayList<GrantedAuthority>();
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ANONYMOUS_ROLE_NAME));
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + AUTHENTICATED_ROLE_NAME));
            roles.stream()
                    .map(role -> AuthorityUtils.ROLE_PREFIX + role)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            addScopeAuthorities(pat, authorities);
            var owner = pat.getSpec().getUsername();
            var effectiveUser = StringUtils.hasText(sudoUsername) ? sudoUsername : owner;

            var jwtBuilder = Jwt.withTokenValue(tokenValue)
                    .header("alg", "none")
                    .claim("sub", effectiveUser)
                    .claim("pat_name", pat.getMetadata().getName())
                    .claim("pat_owner", owner)
                    .claim("tokenId", pat.getSpec().getTokenId())
                    .issuedAt(clock.instant());
            if (StringUtils.hasText(sudoUsername)) {
                jwtBuilder.claim("sudo_user", sudoUsername);
            }
            return new JwtAuthenticationToken(jwtBuilder.build(), authorities, effectiveUser);
        });
    }

    // 代操作时取双方角色交集，否则沿用令牌角色。
    private Mono<List<String>> effectiveRoles(PersonalAccessToken pat, @Nullable String sudoUsername) {
        var patRoles = pat.getSpec().getRoles();
        if (!StringUtils.hasText(sudoUsername)) {
            return Mono.just(patRoles == null ? List.of() : patRoles);
        }
        return rolesOf(pat.getSpec().getUsername())
                .collectList()
                .zipWith(rolesOf(sudoUsername).collectList())
                .map(tuple -> {
                    var ownerRoles = tuple.getT1();
                    var allowed = CollectionUtils.isEmpty(patRoles) ? ownerRoles : patRoles;
                    return tuple.getT2().stream().filter(allowed::contains).toList();
                });
    }

    // 查询用户当前持有的角色名称。
    private Flux<String> rolesOf(String username) {
        return userRoles.list(username);
    }

    // 禁止非超级管理员代操作为超级管理员。
    private Mono<Void> checkSudoTargetRoles(PersonalAccessToken pat, String sudoUsername) {
        return rolesOf(sudoUsername)
                .collectList()
                .flatMap(targetRoles -> targetRoles.contains(BuiltinEntities.SUPER_ROLE)
                        ? rolesOf(pat.getSpec().getUsername())
                                .collectList()
                                .flatMap(ownerRoles -> ownerRoles.contains(BuiltinEntities.SUPER_ROLE)
                                        ? Mono.empty()
                                        : Mono.error(new InvalidBearerTokenException(
                                                "无权 sudo 为超级管理员。")))
                        : Mono.empty());
    }

    // 校验代操作开关及目标用户的存在与禁用状态。
    private Mono<Void> checkSudo(PersonalAccessToken pat, @Nullable String sudoUsername) {
        if (!StringUtils.hasText(sudoUsername)) {
            return Mono.empty();
        }
        if (!pat.getSpec().isSudo()) {
            return Mono.error(new InvalidBearerTokenException("个人访问令牌不支持 sudo。"));
        }
        return client.fetch(User.class, sudoUsername)
                .switchIfEmpty(Mono.error(() -> new InvalidBearerTokenException("sudo 目标用户不存在。")))
                .flatMap(user -> Boolean.TRUE.equals(user.getSpec().getDisabled())
                        ? Mono.error(new InvalidBearerTokenException("sudo 目标用户已被禁用。"))
                        : checkSudoTargetRoles(pat, sudoUsername));
    }

    // 旧版令牌重新校验个人令牌并重建权限集合。
    private Mono<JwtAuthenticationToken> checkAndRebuild(JwtAuthenticationToken jat) {
        var jwt = jat.getToken();
        var patName = jwt.getClaimAsString("pat_name");
        var jwtId = jwt.getClaimAsString(JWTClaimNames.JWT_ID);
        if (patName == null || jwtId == null) {

            return Mono.error(new InvalidBearerTokenException("缺少 pat_name 或 jti 声明"));
        }
        return client.fetch(PersonalAccessToken.class, patName)
                .switchIfEmpty(Mono.error(() -> new DisabledException("个人访问令牌已被删除。")))
                .flatMap(pat -> patChecks(pat, jwtId)
                        .then(checkUser(
                                pat.getSpec().getUsername(), pat.getSpec().getRoles()))
                        .then(updateLastUsed(patName))
                        .thenReturn(pat))
                .map(pat -> {

                    var authorities = new ArrayList<>(jat.getAuthorities());
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ANONYMOUS_ROLE_NAME));
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + AUTHENTICATED_ROLE_NAME));
                    var roles = pat.getSpec().getRoles();
                    if (roles != null) {
                        roles.stream()
                                .map(role -> AuthorityUtils.ROLE_PREFIX + role)
                                .map(SimpleGrantedAuthority::new)
                                .forEach(authorities::add);
                    }
                    addScopeAuthorities(pat, authorities);
                    return new JwtAuthenticationToken(jat.getToken(), authorities, jat.getName());
                });
    }

    // 校验令牌所有者存在、未禁用且角色未越权。
    private Mono<Void> checkUser(String username, List<String> patRoles) {
        return client.fetch(User.class, username)
                .switchIfEmpty(Mono.error(() -> new InvalidBearerTokenException("用户不存在。")))
                .flatMap(user -> {
                    if (Boolean.TRUE.equals(user.getSpec().getDisabled())) {
                        return Mono.error(new DisabledException("用户已被禁用"));
                    }
                    if (CollectionUtils.isEmpty(patRoles)) {
                        return Mono.empty();
                    }
                    return rolesOf(username)
                            .concatWithValues(AUTHENTICATED_ROLE_NAME, ANONYMOUS_ROLE_NAME)
                            .collectList()
                            .flatMap(roles -> roleService.contains(roles, patRoles))
                            .filter(Boolean::booleanValue)
                            .switchIfEmpty(Mono.error(() -> new InvalidBearerTokenException(
                                    "个人访问令牌的角色超出了用户当前拥有的角色。")))
                            .then();
                });
    }

    // 节流更新最后使用时间，冲突时重试并忽略失败。
    private Mono<Void> updateLastUsed(String patName) {


        var now = clock.instant();
        return Mono.defer(

                        () -> client.fetch(PersonalAccessToken.class, patName)
                                .filter(pat -> {
                                    var lastUsed = pat.getSpec().getLastUsed();
                                    if (lastUsed == null) {
                                        return true;
                                    }
                                    var diff = Duration.between(lastUsed, now);
                                    return !diff.minus(MIN_UPDATE_GAP).isNegative();
                                })
                                .doOnNext(pat -> pat.getSpec().setLastUsed(now))
                                .flatMap(client::update))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(50))
                        .filter(OptimisticLockingFailureException.class::isInstance))
                .onErrorComplete()
                .then();
    }

    // 校验令牌标识与撤销状态是否与存储记录一致。
    private Mono<Void> patChecks(PersonalAccessToken pat, String tokenId) {
        if (ExtensionUtil.isDeleted(pat)) {
            return Mono.error(new InvalidBearerTokenException("个人访问令牌正在删除中。"));
        }
        var spec = pat.getSpec();
        if (!Objects.equals(spec.getTokenId(), tokenId)) {
            return Mono.error(
                    new InvalidBearerTokenException("令牌 ID 与个人访问令牌的令牌 ID 不匹配。"));
        }
        if (spec.isRevoked()) {
            return Mono.error(new InvalidBearerTokenException("令牌已被撤销。"));
        }
        return Mono.empty();
    }
}
