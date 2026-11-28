package cn.tolim.pudans.iam.infra.security.util;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.Map;

public final class HttpSecurityConfigUtils {

    private HttpSecurityConfigUtils() {
    }

    public static PasswordEncoder getPasswordEncoder(HttpSecurity httpSecurity) {
        PasswordEncoder passwordEncoder = httpSecurity.getSharedObject(PasswordEncoder.class);
        if (passwordEncoder == null) {
            passwordEncoder = getOptionalBean(httpSecurity, PasswordEncoder.class);
            httpSecurity.setSharedObject(PasswordEncoder.class, passwordEncoder);
        }
        return passwordEncoder;
    }

    public static UserDetailsService getUserDetailsService(HttpSecurity httpSecurity) {
        UserDetailsService userDetailsService = httpSecurity
                .getSharedObject(UserDetailsService.class);
        if (userDetailsService == null) {
            userDetailsService = getOptionalBean(httpSecurity, UserDetailsService.class);
            httpSecurity.setSharedObject(UserDetailsService.class, userDetailsService);
        }
        return userDetailsService;
    }

    public static <T> T getOptionalBean(HttpSecurity httpSecurity, Class<T> type) {
        ApplicationContext context = httpSecurity.getSharedObject(ApplicationContext.class);
        Map<String, T> beansMap = BeanFactoryUtils
                .beansOfTypeIncludingAncestors((ListableBeanFactory) context, type);
        if (beansMap.size() > 1) {
            throw new NoUniqueBeanDefinitionException(type, beansMap.size(),
                    "期望只匹配到一个类型为 '" + type.getName() + "'，但找到 "
                            + beansMap.size() + "："
                            + StringUtils
                            .collectionToCommaDelimitedString(beansMap.keySet()));
        }
        return beansMap.isEmpty() ? null : beansMap.values().iterator().next();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getOptionalBean(HttpSecurity httpSecurity, ResolvableType type) {
        ApplicationContext context = httpSecurity.getSharedObject(ApplicationContext.class);
        String[] beanNames = context.getBeanNamesForType(type);
        if (beanNames.length > 1) {
            throw new NoUniqueBeanDefinitionException(type, beanNames);
        }
        return beanNames.length == 1 ? (T) context.getBean(beanNames[0]) : null;
    }
}
