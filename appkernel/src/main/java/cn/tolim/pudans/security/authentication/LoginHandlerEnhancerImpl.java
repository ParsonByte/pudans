package cn.tolim.pudans.security.authentication;

import static cn.tolim.pudans.security.authentication.SecurityConstant.USERNAME_PARAMETER_NAME;

import cn.tolim.pudans.contract.security.LoginHandlerEnhancer;
import cn.tolim.pudans.contract.security.device.DeviceService;
import cn.tolim.pudans.core.user.service.UserLoginOrLogoutProcessing;
import cn.tolim.pudans.security.authentication.oauth2.OAuth2LoginHandlerEnhancer;
import cn.tolim.pudans.security.authentication.rememberme.RememberMeServices;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class LoginHandlerEnhancerImpl implements LoginHandlerEnhancer {

    private final RememberMeServices rememberMeServices;

    private final DeviceService deviceService;

    private final LoginParameterRequestCache parameterRequestCache;

    private final OAuth2LoginHandlerEnhancer oauth2LoginHandlerEnhancer;

    private final UserLoginOrLogoutProcessing userLoginOrLogoutProcessing;

    @Override
    public Mono<Void> onLoginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication) {
        return rememberMeServices
                .loginSuccess(exchange, successfulAuthentication)
                .then(deviceService.loginSuccess(exchange, successfulAuthentication))
                .then(oauth2LoginHandlerEnhancer.loginSuccess(exchange, successfulAuthentication))
                .then(userLoginOrLogoutProcessing.loginProcessing(successfulAuthentication.getName()))
                .then(parameterRequestCache.removeParameter(exchange, USERNAME_PARAMETER_NAME))
                .then(parameterRequestCache.removeParameter(exchange, "email"));
    }

    @Override
    public Mono<Void> onLoginFailure(ServerWebExchange exchange, AuthenticationException exception) {
        return parameterRequestCache
                .saveParameter(exchange, USERNAME_PARAMETER_NAME)
                .then(parameterRequestCache.saveParameter(exchange, "email"))
                .then(rememberMeServices.loginFail(exchange));
    }
}
