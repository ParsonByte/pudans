package cn.tolim.pudans.iam.protocol.core.configurer;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.authentication.WebAuthenticationDetailsSource;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.util.StringUtils;

import java.util.Map;


public class AuthenticationUtils {

    public static <B extends HttpSecurityBuilder<B>> SessionRegistry getSessionRegistry(B builder) {
        SessionRegistry sessionRegistry = builder.getSharedObject(SessionRegistry.class);
        if (sessionRegistry == null) {
            sessionRegistry = getBean(builder, SessionRegistry.class);
            builder.setSharedObject(SessionRegistry.class, sessionRegistry);
        }
        return sessionRegistry;
    }

    public static <B extends HttpSecurityBuilder<B>> ApplicationServiceLoader getApplicationServiceLoader(B builder) {
        ApplicationServiceLoader applicationServiceLoader = builder
                .getSharedObject(ApplicationServiceLoader.class);
        if (applicationServiceLoader == null) {
            applicationServiceLoader = getBean(builder, ApplicationServiceLoader.class);
            builder.setSharedObject(ApplicationServiceLoader.class, applicationServiceLoader);
        }
        return applicationServiceLoader;
    }

    public static <B extends HttpSecurityBuilder<B>> WebAuthenticationDetailsSource getAuthenticationDetailsSource(B builder) {
        WebAuthenticationDetailsSource authenticationDetailsSource = builder
                .getSharedObject(WebAuthenticationDetailsSource.class);
        if (authenticationDetailsSource == null) {
            authenticationDetailsSource = getBean(builder, WebAuthenticationDetailsSource.class);
            builder.setSharedObject(WebAuthenticationDetailsSource.class,
                    authenticationDetailsSource);
        }
        return authenticationDetailsSource;
    }

    public static <T> T getOptionalBean(HttpSecurity httpSecurity, Class<T> type) {
        Map<String, T> beansMap = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                httpSecurity.getSharedObject(ApplicationContext.class), type);
        if (beansMap.size() > 1) {
            throw new NoUniqueBeanDefinitionException(
                    type, beansMap
                    .size(),
                    "期望只匹配到一个类型为 '" + type.getName() + "'，但找到 "
                            + beansMap.size() + "："
                            + StringUtils.collectionToCommaDelimitedString(beansMap.keySet()));
        }
        return (!beansMap.isEmpty() ? beansMap.values().iterator().next() : null);
    }

    public static <B extends HttpSecurityBuilder<B>, T> T getBean(B builder, Class<T> type) {
        return builder.getSharedObject(ApplicationContext.class).getBean(type);
    }
}
