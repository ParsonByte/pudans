package cn.tolim.pudans.core.user;

import static java.nio.charset.StandardCharsets.UTF_8;

import cn.tolim.pudans.core.rbac.Roles;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;

import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.user.PersonalTokens;
import cn.tolim.pudans.infra.exception.NotFoundException;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.infra.setting.MasterKeys;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.IdGenerator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


@Service
class ExtensionPersonalTokens implements PersonalTokens {

    private final Roles roleService;

    private final IdGenerator idGenerator;

    private final ReactiveExtensionClient client;

    private final AuthenticationTrustResolver authTrustResolver = new AuthenticationTrustResolverImpl();

    private final ReactiveUserDetailsService reactiveUserDetailsService;

    private final MasterKeys masterKeys;

    private final SecureRandom secureRandom = new SecureRandom();

    private Clock clock;

    // 初始化依赖与默认时钟
    public ExtensionPersonalTokens(
            Roles roleService,
            ReactiveExtensionClient client,
            ReactiveUserDetailsService reactiveUserDetailsService,
            MasterKeys masterKeys) {
        this.roleService = roleService;
        this.client = client;
        this.reactiveUserDetailsService = reactiveUserDetailsService;
        this.masterKeys = masterKeys;
        this.clock = Clock.systemUTC();
        idGenerator = new AlternativeJdkIdGenerator();
    }

    // 将令牌名和密钥编码为完整令牌
    static String buildToken(String patName, String secret) {
        var encoded = Base64.getUrlEncoder().withoutPadding().encodeToString((patName + ':' + secret).getBytes(UTF_8));
        return PersonalAccessToken.PAT_TOKEN_PREFIX + encoded;
    }

    // 替换时钟便于测试过期与撤销时间
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // 从安全上下文创建令牌并拒绝特权会话
    @Override
    public Mono<PersonalAccessToken> create(PersonalAccessToken patRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)

                .filter(authTrustResolver::isAuthenticated)
                .flatMap(auth -> isSudoAuthentication(auth)
                        ? Mono.error(new UnsatisfiedAttributeValueException(
                                "sudo 会话不能创建个人访问令牌。"))
                        : Mono.just(auth))
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("请先登录。")))
                .flatMap(auth -> ensureScopesWithinCaller(patRequest, auth.getAuthorities())
                        .then(Mono.defer(() -> create(patRequest, auth.getName(), auth.getAuthorities()))));
    }

    // 带 scope 的令牌只能签发 scope 更窄的子令牌，不能通过 RolesOnly 绕过限制。
    private Mono<Void> ensureScopesWithinCaller(
            PersonalAccessToken patRequest, Collection<? extends GrantedAuthority> authorities) {
        var callerScopes = AuthorityUtils.authoritiesToScopes(authorities);
        if (callerScopes.isEmpty()) {
            return Mono.empty();
        }
        var unrestricted = callerScopes.contains("*") || callerScopes.contains("*:*");
        var requested = patRequest.getSpec() == null ? null : patRequest.getSpec().getScopes();
        if (!unrestricted
                && (CollectionUtils.isEmpty(requested) || !callerScopes.containsAll(requested))) {
            return Mono.error(new UnsatisfiedAttributeValueException(
                    "子令牌的 scope 不能超出当前令牌。"));
        }
        if (PersonalAccessToken.SCOPE_MODE_ROLES_ONLY.equals(patRequest.getSpec().getScopeMode())) {
            return Mono.error(new UnsatisfiedAttributeValueException(
                    "带 scope 的令牌不能创建 RolesOnly 子令牌。"));
        }
        return Mono.empty();
    }

    // 为指定用户创建令牌并沿用其权限
    @Override
    public Mono<PersonalAccessToken> create(PersonalAccessToken patRequest, String username) {
        return reactiveUserDetailsService
                .findByUsername(username)
                .flatMap(userDetails -> create(patRequest, username, userDetails.getAuthorities()));
    }

    // 校验有效期、特权与角色后复制请求数据
    private Mono<PersonalAccessToken> create(
            PersonalAccessToken patRequest, String username, Collection<? extends GrantedAuthority> authorities) {
        var patSpec = patRequest.getSpec();

        var expiresAt = patSpec.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(clock.instant())) {
            return Mono.error(new UnsatisfiedAttributeValueException("令牌过期时间无效，请选择未来的时间。"));
        }
        var roles = patSpec.getRoles();
        if (patSpec.isSudo() && !isAdmin(authorities)) {
            return Mono.error(new UnsatisfiedAttributeValueException(
                    "只有管理员可以创建带 sudo 能力的令牌。"));
        }
        var gateway = patSpec.getGateway();
        if (gateway != null && gateway.getLimits() != null && gateway.getLimits().size() > 5) {
            return Mono.error(new UnsatisfiedAttributeValueException("网关预算限制最多 5 条。"));
        }
        return hasSufficientRoles(authorities, roles)
                .filter(has -> has)
                .switchIfEmpty(
                        Mono.error(() -> new UnsatisfiedAttributeValueException("你没有授予所选角色的权限。")))
                .map(has -> {
                    var pat = new PersonalAccessToken();
                    pat.setMetadata(new Metadata());
                    if (patRequest.getMetadata() != null) {
                        var metadata = patRequest.getMetadata();
                        if (metadata.getName() != null) {
                            pat.getMetadata().setName(metadata.getName());
                        }
                        if (metadata.getGenerateName() != null) {
                            pat.getMetadata().setGenerateName(metadata.getGenerateName());
                        }
                        if (metadata.getLabels() != null) {
                            pat.getMetadata().setLabels(new HashMap<>());
                            pat.getMetadata().getLabels().putAll(metadata.getLabels());
                        }
                        if (metadata.getAnnotations() != null) {
                            pat.getMetadata().setAnnotations(new HashMap<>());
                            pat.getMetadata().getAnnotations().putAll(metadata.getAnnotations());
                        }
                        if (metadata.getFinalizers() != null) {
                            pat.getMetadata().setFinalizers(new HashSet<>());
                            pat.getMetadata().getFinalizers().addAll(metadata.getFinalizers());
                        }
                    }
                    if (pat.getMetadata().getGenerateName() == null) {
                        pat.getMetadata().setGenerateName("pat-" + username + "-");
                    }
                    pat.getSpec().setUsername(username);
                    pat.getSpec().setName(patSpec.getName());
                    pat.getSpec().setDescription(patSpec.getDescription());
                    if (patSpec.getRoles() != null) {
                        pat.getSpec().setRoles(new ArrayList<>());
                        pat.getSpec().getRoles().addAll(patSpec.getRoles());
                    }
                    if (patSpec.getScopes() != null) {
                        pat.getSpec().setScopes(new ArrayList<>());
                        pat.getSpec().getScopes().addAll(patSpec.getScopes());
                    }
                    if (patSpec.getScopeMode() != null) {
                        pat.getSpec().setScopeMode(patSpec.getScopeMode());
                    }
                    pat.getSpec().setExpiresAt(patSpec.getExpiresAt());
                    pat.getSpec().setSudo(patSpec.isSudo());
                    if (gateway != null) {
                        pat.getSpec().setGateway(JsonUtils.deepCopy(gateway));
                    }
                    pat.getSpec().setTokenId(idGenerator.generateId().toString());
                    return pat;
                })
                .flatMap(client::create);
    }

    // 仅撤销尚未撤销的令牌并记录时间
    @Override
    public Mono<PersonalAccessToken> revoke(String patName, String username) {
        return get(patName, username)
                .filter(pat -> !pat.getSpec().isRevoked())
                .switchIfEmpty(
                        Mono.error(() -> new UnsatisfiedAttributeValueException("令牌已撤销。")))
                .doOnNext(pat -> {
                    pat.getSpec().setRevoked(true);
                    pat.getSpec().setRevokesAt(clock.instant());
                })
                .flatMap(client::update);
    }

    // 仅恢复已撤销令牌并清除撤销时间
    @Override
    public Mono<PersonalAccessToken> restore(String patName, String username) {
        return get(patName, username)
                .filter(pat -> pat.getSpec().isRevoked())
                .switchIfEmpty(Mono.error(() -> new UnsatisfiedAttributeValueException("令牌尚未撤销。")))
                .doOnNext(pat -> {
                    pat.getSpec().setRevoked(false);
                    pat.getSpec().setRevokesAt(null);
                })
                .flatMap(client::update);
    }

    // 校验归属后删除令牌
    @Override
    public Mono<PersonalAccessToken> delete(String patName, String username) {
        return get(patName, username).flatMap(client::delete);
    }

    // 只返回属于该用户的令牌
    @Override
    public Mono<PersonalAccessToken> get(String patName, String username) {
        return client.fetch(PersonalAccessToken.class, patName)
                .filter(pat -> Objects.equals(pat.getSpec().getUsername(), username))
                .switchIfEmpty(Mono.error(() -> new NotFoundException("个人访问令牌不存在或已删除。")));
    }

    // 列出用户创建的全部令牌
    @Override
    public Flux<PersonalAccessToken> list(String username) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.username", username))
                .build();
        return client.listAll(PersonalAccessToken.class, options, defaultSort());
    }

    // 删除过期令牌并返回删除数量
    @Override
    public Mono<Long> deleteExpired(String username) {
        var now = clock.instant();
        return list(username)
                .filter(pat -> pat.getSpec() != null
                        && pat.getSpec().getExpiresAt() != null
                        && pat.getSpec().getExpiresAt().isBefore(now))
                .flatMap(client::delete)
                .count();
    }

    // 撤销用户全部未撤销令牌，返回实际撤销数量
    @Override
    public Mono<Long> revokeAll(String username) {
        var now = clock.instant();
        return list(username)
                .filter(pat -> pat.getSpec() != null && !pat.getSpec().isRevoked())
                .flatMap(pat -> {
                    pat.getSpec().setRevoked(true);
                    pat.getSpec().setRevokesAt(now);
                    return client.update(pat);
                })
                .count();
    }

    // 生成一次性密钥并保存其哈希
    @Override
    public Mono<String> generateToken(PersonalAccessToken pat) {

        return Mono.fromCallable(this::generateSecret)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(secret -> {

                    pat.getSpec().setTokenId(masterKeys.hash(secret));
                    pat.getSpec().setKeyVersion(masterKeys.currentVersion());
                    return client.update(pat)
                            .thenReturn(buildToken(pat.getMetadata().getName(), secret));
                });
    }

    // 生成足够随机的令牌密钥
    private String generateSecret() {
        var bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 未申请角色时默认通过，否则校验授权
    private Mono<Boolean> hasSufficientRoles(
            Collection<? extends GrantedAuthority> grantedAuthorities, List<String> requestRoles) {
        if (CollectionUtils.isEmpty(requestRoles)) {
            return Mono.just(true);
        }
        var grantedRoles = AuthorityUtils.authoritiesToRoles(grantedAuthorities);
        return roleService.contains(grantedRoles, requestRoles);
    }

    // 判断是否具备管理员角色
    private static boolean isAdmin(Collection<? extends GrantedAuthority> authorities) {
        var roles = AuthorityUtils.authoritiesToRoles(authorities);
        return roles.contains(BuiltinEntities.SUPER_ROLE)
                || roles.contains(BuiltinEntities.SYSTEM_ADMIN_ROLE);
    }

    // sudo 令牌只能临时代表目标用户，不能借机创建持久凭据。
    private static boolean isSudoAuthentication(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt
                && jwt.getToken().hasClaim("sudo_user");
    }
}
