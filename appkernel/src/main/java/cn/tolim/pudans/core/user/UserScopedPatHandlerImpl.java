package cn.tolim.pudans.core.user;

import static cn.tolim.pudans.extension.api.index.query.Queries.equal;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;

import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.security.api.PersonalAccessToken;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.user.PersonalTokens;
import cn.tolim.pudans.core.user.UserScopedPatHandler;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Service
class UserScopedPatHandlerImpl implements UserScopedPatHandler {

    private static final String ACCESS_TOKEN_ANNO_NAME = "security.pudans.tolim.cn/access-token";

    private final ReactiveExtensionClient client;

    private final PersonalTokens personalTokens;

    private final AuditLog auditLog;

    private final SecurityParameters securitySettings;

    private final AuthenticationTrustResolver authTrustResolver = new AuthenticationTrustResolverImpl();

    public UserScopedPatHandlerImpl(
            ReactiveExtensionClient client,
            PersonalTokens personalTokens,
            AuditLog auditLog,
            SecurityParameters securitySettings) {
        this.client = client;
        this.personalTokens = personalTokens;
        this.auditLog = auditLog;
        this.securitySettings = securitySettings;
    }

    private static Map<String, Object> snapshot(PersonalAccessToken pat) {
        var spec = pat.getSpec();
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("name", pat.getMetadata().getName());
        snapshot.put("username", spec.getUsername());
        snapshot.put("description", spec.getDescription());
        snapshot.put("roles", spec.getRoles());
        snapshot.put("scopes", spec.getScopes());
        snapshot.put("scopeMode", spec.getScopeMode());
        snapshot.put("expiresAt", spec.getExpiresAt() == null ? null : spec.getExpiresAt().toString());
        snapshot.put("revoked", spec.isRevoked());
        return snapshot;
    }

    private Mono<Authentication> mustBeAuthenticated(Mono<Authentication> authentication) {
        return authentication
                .filter(authTrustResolver::isAuthenticated)

                .switchIfEmpty(Mono.error(AccessDeniedException::new));
    }

    @Override
    public Mono<ServerResponse> create(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .transform(this::mustBeAuthenticated)
                .flatMap(auth -> request.bodyToMono(PersonalAccessToken.class))
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("缺少请求体。")))
                .flatMap(this::validatePolicy)
                .flatMap(personalTokens::create)
                .flatMap(pat -> personalTokens
                        .generateToken(pat)
                        .doOnNext(token -> {
                            if (pat.getMetadata().getAnnotations() == null) {
                                pat.getMetadata().setAnnotations(new HashMap<>());
                            }
                            pat.getMetadata().getAnnotations().put(ACCESS_TOKEN_ANNO_NAME, token);
                        })
                        .thenReturn(pat))
                .flatMap(pat -> auditLog
                        .recordChild(
                                request.exchange(),
                                null,
                                "PAT_CREATE",
                                PersonalAccessToken.KIND,
                                pat.getMetadata().getName(),
                                "SUCCESS",
                                snapshot(pat),
                                null)
                        .thenReturn(pat))
                .flatMap(pat -> ServerResponse.ok().bodyValue(pat));
    }

    Mono<PersonalAccessToken> validatePolicy(PersonalAccessToken pat) {
        if (!securitySettings.isPatEnabled()) {
            return Mono.error(new ServerWebInputException("个人访问令牌已禁用。"));
        }
        var maxLifetimeDays = securitySettings.patMaxLifetimeDays();
        if (maxLifetimeDays > 0) {
            var expiresAt = pat.getSpec() == null ? null : pat.getSpec().getExpiresAt();
            if (expiresAt == null
                    || expiresAt.isAfter(Instant.now().plus(Duration.ofDays(maxLifetimeDays)))) {
                return Mono.error(new ServerWebInputException(
                        "个人访问令牌有效期不能超过 " + maxLifetimeDays + " 天。"));
            }
        }
        return Mono.just(pat);
    }

    @Override
    public Mono<ServerResponse> list(ServerRequest request) {        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    var listOptions = ListOptions.builder()
                            .fieldQuery(equal("spec.username", auth.getName()))
                            .build();
                    var pats = client.listAll(
                            PersonalAccessToken.class,
                            listOptions,
                            Sort.by(desc("metadata.creationTimestamp"), asc("metadata.name")));
                    return ServerResponse.ok().body(pats, PersonalAccessToken.class);
                });
    }

    @Override
    public Mono<ServerResponse> get(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    var name = request.pathVariable("name");
                    return personalTokens.get(name, auth.getName());
                })
                .flatMap(pat -> ServerResponse.ok().bodyValue(pat));
    }

    @Override
    public Mono<ServerResponse> revoke(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    var name = request.pathVariable("name");
                    return personalTokens.revoke(name, auth.getName());
                })
                .flatMap(revokedPat -> auditLog
                        .recordChild(
                                request.exchange(),
                                null,
                                "PAT_REVOKE",
                                PersonalAccessToken.KIND,
                                revokedPat.getMetadata().getName(),
                                "SUCCESS",
                                snapshot(revokedPat),
                                null)
                        .thenReturn(revokedPat))
                .flatMap(revokedPat -> ServerResponse.ok().bodyValue(revokedPat));
    }

    @Override
    public Mono<ServerResponse> delete(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    var name = request.pathVariable("name");
                    return personalTokens.delete(name, auth.getName());
                })
                .flatMap(pat -> ServerResponse.ok().bodyValue(pat));
    }

    @Override
    public Mono<ServerResponse> restore(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .transform(this::mustBeAuthenticated)
                .flatMap(auth -> {
                    var name = request.pathVariable("name");
                    return personalTokens.restore(name, auth.getName());
                })
                .flatMap(pat -> ServerResponse.ok().bodyValue(pat));
    }
}
