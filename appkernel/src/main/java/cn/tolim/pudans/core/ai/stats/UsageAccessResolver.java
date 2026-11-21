package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UsageAccessResolver {

    public static final String SCOPE_SELF = "self";

    public static final String SCOPE_ALL = "all";

    private static final String AI_ADMIN_ROLE = "role-template-ai-admin";

    private final AiEndpointRequests requests;

    public Mono<Viewer> viewer(ServerRequest request) {
        var scope = scope(request);
        return requests
                .userId(request, AiUsageErrorCodes.UNAUTHENTICATED, "未认证。")
                .zipWith(roles())
                .map(tuple -> {
                    var admin = isAdmin(tuple.getT2());
                    return new Viewer(tuple.getT1(), SCOPE_ALL.equals(scope) && admin, admin);
                });
    }

    public static boolean isAdmin(Set<String> roles) {
        return roles.contains(BuiltinEntities.SUPER_ROLE)
                || roles.contains(BuiltinEntities.SYSTEM_ADMIN_ROLE)
                || roles.contains(AI_ADMIN_ROLE);
    }

    private static String scope(ServerRequest request) {
        var value = request
                .queryParam("scope")
                .map(String::trim)
                .filter(StringUtils::hasText)
                .orElse(SCOPE_SELF);
        if (SCOPE_SELF.equals(value) || SCOPE_ALL.equals(value)) {
            return value;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                AiUsageErrorCodes.SCOPE_INVALID,
                "scope 必须是 self 或 all。");
    }

    private static Mono<Set<String>> roles() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .map(authentication -> AuthorityUtils.authoritiesToRoles(authentication.getAuthorities()))
                .defaultIfEmpty(Set.of());
    }

    public record Viewer(String userId, boolean all, boolean admin) {}
}