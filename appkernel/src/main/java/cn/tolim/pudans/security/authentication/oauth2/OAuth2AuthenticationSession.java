package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.security.authentication.LoginFlowHandler;
import cn.tolim.pudans.contract.security.authentication.oauth2.PudansOAuth2AuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSession {

    private final ReactiveUserDetailsService userDetailsService;

    private final ServerSecurityContextRepository securityContextRepository;

    private final LoginFlowHandler loginFlowHandler;

    public Mono<Void> establish(ServerWebExchange exchange, String username, OAuth2AuthenticationToken original) {
        return establishAndGetAuthentication(exchange, username, original).then();
    }

    public Mono<Authentication> establishAndGetAuthentication(
            ServerWebExchange exchange, String username, OAuth2AuthenticationToken original) {
        return userDetailsService
                .findByUsername(username)
                .map(userDetails -> PudansOAuth2AuthenticationToken.authenticated(userDetails, original))
                .flatMap(authentication -> securityContextRepository
                        .save(exchange, new SecurityContextImpl(authentication))
                        .then(loginFlowHandler.onLoginSuccess(exchange, authentication))
                        .thenReturn(authentication));
    }
}
