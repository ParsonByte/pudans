package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.core.user.Connections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Slf4j
@Component
public class DefaultOAuth2LoginFlowEnhancer implements OAuth2LoginFlowEnhancer {

    private final Connections connections;

    private final OAuth2AuthenticationTokenCache oauth2TokenCache;

    private final OAuth2BindIntent bindIntent;

    private final AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    public DefaultOAuth2LoginFlowEnhancer(
            Connections connections,
            OAuth2AuthenticationTokenCache oauth2TokenCache,
            OAuth2BindIntent bindIntent) {
        this.connections = connections;
        this.oauth2TokenCache = oauth2TokenCache;
        this.bindIntent = bindIntent;
    }

    @Override
    public Mono<Void> loginSuccess(ServerWebExchange exchange, Authentication authentication) {
        if (!authenticationTrustResolver.isFullyAuthenticated(authentication)) {


            return Mono.when(oauth2TokenCache.removeToken(exchange), bindIntent.clear(exchange));
        }
        return bindIntent
                .consume(exchange)
                .flatMap(oauth2Identity ->
                        bind(exchange, authentication, oauth2Identity).thenReturn(true))
                .defaultIfEmpty(false)
                .flatMap(bindAttempted -> bindAttempted ? Mono.empty() : oauth2TokenCache.removeToken(exchange));
    }

    private Mono<Void> bind(ServerWebExchange exchange, Authentication authentication, String expectedOAuth2Identity) {
        var binding = oauth2TokenCache.getToken(exchange).flatMap(oauth2Token -> {
            if (!OAuth2IdentityFingerprint.from(oauth2Token).equals(expectedOAuth2Identity)) {
                log.warn("拒绝绑定在本地登录期间发生变化的 OAuth2 身份");
                return Mono.empty();
            }
            var oauth2User = oauth2Token.getPrincipal();
            var username = authentication.getName();
            var registrationId = oauth2Token.getAuthorizedClientRegistrationId();
            return connections
                    .updateUserConnectionIfPresent(registrationId, oauth2User)
                    .doOnNext(connection -> {
                        if (log.isDebugEnabled()) {
                            log.debug("用户连接已存在，跳过创建。连接：[{}]", connection);
                        }
                    })
                    .switchIfEmpty(Mono.defer(
                            () -> connections.createUserConnection(username, registrationId, oauth2User)))
                    .then();
        });
        return binding.then(Mono.defer(() -> oauth2TokenCache.removeToken(exchange)))
                .onErrorResume(error ->
                        Mono.defer(() -> oauth2TokenCache.removeToken(exchange)).then(Mono.error(error)));
    }
}
