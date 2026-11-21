package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.security.contract.authentication.oauth2.PudansOAuth2AuthenticationToken;
import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.security.authentication.LoginFlowHandler;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.UserAccountStatusChecker;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSession {

    private final ReactiveUserDetailsService reactiveUserDetailsService;

    private final ServerSecurityContextRepository securityContextRepository;

    private final LoginFlowHandler loginFlowHandler;

    public Mono<Void> establish(ServerWebExchange exchange, String username, OAuth2AuthenticationToken original) {
        return establishAndGetAuthentication(exchange, username, original).then();
    }

    public Mono<Authentication> establishAndGetAuthentication(
            ServerWebExchange exchange, String username, OAuth2AuthenticationToken original) {
        return establishAndGetAuthentication(exchange, username, original, false);
    }

    public Mono<Authentication> establishAndGetAuthentication(
            ServerWebExchange exchange,
            String username,
            OAuth2AuthenticationToken original,
            boolean twoFactorSatisfied) {
        return reactiveUserDetailsService
                .findByUsername(username)
                .switchIfEmpty(Mono.error(() -> new UsernameNotFoundException("用户不存在。")))
                // 登录前复核账号状态，避免停用账号凭旧授权重放建立会话
                .flatMap(userDetails -> UserAccountStatusChecker.check(userDetails).thenReturn(userDetails))
                .map(userDetails -> {
                    if (userDetails instanceof PudansUserDetailsContract contract
                            && contract.isTwoFactorAuthEnabled()
                            && !twoFactorSatisfied) {
                        var previous = UsernamePasswordAuthenticationToken.authenticated(
                                userDetails, "", userDetails.getAuthorities());
                        return (Authentication) new TwoFactorAuthentication(previous);
                    }
                    return PudansOAuth2AuthenticationToken.authenticated(userDetails, original);
                })
                .flatMap(authentication -> securityContextRepository
                        .save(exchange, new SecurityContextImpl(authentication))
                        .then(authentication instanceof TwoFactorAuthentication
                                ? Mono.empty()
                                : loginFlowHandler.onLoginSuccess(exchange, authentication))
                        .thenReturn(authentication));
    }

    static Mono<Void> rejectUnavailableAccount(ServerWebExchange exchange, ServerResponse.Context context) {
        return ProblemDetailResponses.writeOrRedirect(
                exchange,
                context,
                HttpStatus.UNAUTHORIZED,
                "account-unavailable",
                "该账号不可用，请联系管理员。",
                ProblemDetailResponses.SIGN_IN_PATH);
    }
}
