package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Connections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
final class OAuth2ConnectionMapping {

    private final Connections connections;

    Mono<UserConnection> map(ServerWebExchange exchange, OAuth2AuthenticationToken token) {
        var registrationId = token.getAuthorizedClientRegistrationId();
        var oauth2User = token.getPrincipal();
        return connections
                .updateUserConnectionIfPresent(registrationId, oauth2User)
                .switchIfEmpty(Mono.defer(() -> {
                    var preAuthentication = exchange.getAttribute(OAuth2LoginAttributes.PRE_AUTHENTICATION);
                    if (preAuthentication instanceof Authentication preAuth && preAuth.isAuthenticated()) {
                        return connections.createOAuth2Connection(
                                preAuth.getName(), registrationId, oauth2User);
                    }
                    return Mono.error(ConnectionRequiredException::new);
                }));
    }

    static final class ConnectionRequiredException extends RuntimeException {}
}
