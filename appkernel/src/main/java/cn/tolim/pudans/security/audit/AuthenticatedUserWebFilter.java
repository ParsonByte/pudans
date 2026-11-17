package cn.tolim.pudans.security.audit;

import cn.tolim.pudans.core.audit.service.AuditService;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


public class AuthenticatedUserWebFilter implements WebFilter, Ordered {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(org.springframework.security.core.context.SecurityContext::getAuthentication)
                .filter(authentication -> trustResolver.isAuthenticated(authentication)
                        && !trustResolver.isAnonymous(authentication))
                .doOnNext(authentication -> capture(exchange, authentication))
                .then(chain.filter(exchange));
    }

    private void capture(ServerWebExchange exchange, Authentication authentication) {
        exchange.getAttributes().put(AuditService.USERNAME_ATTRIBUTE, authentication.getName());
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            var patOwner = jwtAuthentication.getToken().getClaimAsString("pat_owner");
            if (patOwner != null) {
                exchange.getAttributes().put(AuditService.PAT_OWNER_ATTRIBUTE, patOwner);
            }
            var sudoUser = jwtAuthentication.getToken().getClaimAsString("sudo_user");
            if (sudoUser != null) {
                exchange.getAttributes().put(AuditService.SUDO_USER_ATTRIBUTE, sudoUser);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
