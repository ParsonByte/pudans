package cn.tolim.pudans.security.authentication;

import static cn.tolim.pudans.security.authentication.SecurityConstant.USERNAME_PARAMETER_NAME;

import cn.tolim.pudans.security.api.device.DeviceSessions;
import cn.tolim.pudans.core.user.UserLoginOrLogoutProcessing;
import cn.tolim.pudans.security.authentication.oauth2.OAuth2LoginFlowEnhancer;
import cn.tolim.pudans.security.authentication.rememberme.RememberMeServices;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class LoginFlowHandler {

    private final RememberMeServices rememberMeServices;

    private final DeviceSessions deviceSessions;

    private final LoginParameterRequestCache parameterRequestCache;

    private final OAuth2LoginFlowEnhancer oauth2LoginFlowEnhancer;

    private final UserLoginOrLogoutProcessing userLoginOrLogoutProcessing;

    public Mono<Void> onLoginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication) {
        return rememberMeServices
                .loginSuccess(exchange, successfulAuthentication)
                .then(deviceSessions.loginSuccess(exchange, successfulAuthentication))
                .then(oauth2LoginFlowEnhancer.loginSuccess(exchange, successfulAuthentication))
                .then(userLoginOrLogoutProcessing.loginProcessing(successfulAuthentication.getName()))
                .then(parameterRequestCache.removeParameter(exchange, USERNAME_PARAMETER_NAME))
                .then(parameterRequestCache.removeParameter(exchange, "email"));
    }

    public Mono<Void> onLoginFailure(ServerWebExchange exchange, AuthenticationException exception) {
        return parameterRequestCache
                .saveParameter(exchange, USERNAME_PARAMETER_NAME)
                .then(parameterRequestCache.saveParameter(exchange, "email"))
                .then(rememberMeServices.loginFail(exchange));
    }
}
