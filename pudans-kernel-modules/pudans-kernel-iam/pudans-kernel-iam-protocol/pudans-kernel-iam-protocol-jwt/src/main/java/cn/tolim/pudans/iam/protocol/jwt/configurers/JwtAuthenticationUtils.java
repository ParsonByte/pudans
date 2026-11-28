
package cn.tolim.pudans.iam.protocol.jwt.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils;
import cn.tolim.pudans.iam.protocol.jwt.InMemoryJwtAuthorizationService;
import cn.tolim.pudans.iam.protocol.jwt.JwtAuthorizationService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;


public class JwtAuthenticationUtils {

    public static JwtAuthorizationService getAuthorizationService(HttpSecurity httpSecurity) {
        JwtAuthorizationService authorizationService = httpSecurity
                .getSharedObject(JwtAuthorizationService.class);
        if (authorizationService == null) {
            authorizationService = AuthenticationUtils.getOptionalBean(httpSecurity,
                    JwtAuthorizationService.class);
            if (authorizationService == null) {
                ApplicationServiceLoader applicationServiceLoader = AuthenticationUtils
                        .getOptionalBean(httpSecurity, ApplicationServiceLoader.class);
                authorizationService = new InMemoryJwtAuthorizationService(
                        applicationServiceLoader);
            }
            httpSecurity.setSharedObject(JwtAuthorizationService.class, authorizationService);
        }
        return authorizationService;
    }
}
