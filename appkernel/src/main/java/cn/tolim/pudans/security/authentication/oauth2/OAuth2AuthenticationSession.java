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

    // 建立本地会话，供回调过滤器直接调用。
    public Mono<Void> establish(ServerWebExchange exchange, String username, OAuth2AuthenticationToken original) {
        return establishAndGetAuthentication(exchange, username, original).then();
    }

    // 未声明双因素状态时按未完成处理。
    public Mono<Authentication> establishAndGetAuthentication(
            ServerWebExchange exchange, String username, OAuth2AuthenticationToken original) {
        return establishAndGetAuthentication(exchange, username, original, false);
    }

    // 复核账号状态后保存上下文并触发登录成功流程。
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

    // 账号不可用时统一返回未授权并引导重新登录。
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
