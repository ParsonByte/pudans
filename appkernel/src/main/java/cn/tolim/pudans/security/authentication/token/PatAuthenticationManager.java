package cn.tolim.pudans.security.authentication.token;

import static cn.tolim.pudans.contract.security.PersonalAccessToken.PAT_TOKEN_PREFIX;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.ANONYMOUS_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.AUTHENTICATED_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static java.nio.charset.StandardCharsets.UTF_8;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.setting.MasterKeyService;
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

    private final RoleService roleService;

    private final UserGroupService userGroupService;

    private final MasterKeyService masterKeyService;

    private Clock clock;

    public PatAuthenticationManager(
            ReactiveExtensionClient client,
            ReactiveAuthenticationManager delegate,
            RoleService roleService,
            UserGroupService userGroupService,
            MasterKeyService masterKeyService) {
        this.client = client;
        this.delegate = delegate;
        this.roleService = roleService;
        this.userGroupService = userGroupService;
        this.masterKeyService = masterKeyService;
        this.clock = Clock.systemDefaultZone();
    }

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

    private boolean secretMatches(PersonalAccessToken pat, String secret) {
        return masterKeyService.matches(
                pat.getSpec().getTokenId(), secret, pat.getSpec().getKeyVersion());
    }

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
                        if (masterKeyService.currentVersion() != 0) {
                            return Mono.error(new InvalidBearerTokenException(
                                    "主密钥轮换后，旧版个人访问令牌已失效。"));
                        }
                        return legacyAuthenticate(token, sudoUsername);
                    }
                    return authenticateOpaque(token, sudoUsername);
                });
    }

    private Mono<Authentication> legacyAuthenticate(String token, @Nullable String sudoUsername) {
        if (StringUtils.hasText(sudoUsername)) {
            return Mono.error(new InvalidBearerTokenException("旧版个人访问令牌不支持 sudo。"));
        }
        return delegate.authenticate(new BearerTokenAuthenticationToken(token))
                .cast(JwtAuthenticationToken.class)
                .flatMap(this::checkAndRebuild)
                .map(auth -> auth);
    }

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

    private Mono<List<String>> effectiveRoles(PersonalAccessToken pat, @Nullable String sudoUsername) {
        var patRoles = pat.getSpec().getRoles();
        if (!StringUtils.hasText(sudoUsername)) {
            return Mono.just(patRoles == null ? List.of() : patRoles);
        }
        return rolesOf(sudoUsername)
                .collectList()
                .map(targetRoles -> {
                    if (CollectionUtils.isEmpty(patRoles)) {
                        return targetRoles;
                    }
                    return targetRoles.stream().filter(patRoles::contains).toList();
                });
    }

    private Flux<String> rolesOf(String username) {
        return Flux.concat(
                        roleService.getRolesByUsername(username),
                        userGroupService.getRoleNamesByUsername(username))
                .distinct();
    }

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
                        : Mono.empty());
    }

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
