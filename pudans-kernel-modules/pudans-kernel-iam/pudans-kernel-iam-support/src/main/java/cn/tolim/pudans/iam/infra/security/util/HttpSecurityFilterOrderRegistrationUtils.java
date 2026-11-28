package cn.tolim.pudans.iam.infra.security.util;

import jakarta.servlet.Filter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

public final class HttpSecurityFilterOrderRegistrationUtils {

    private HttpSecurityFilterOrderRegistrationUtils() {
    }

    public static void putFilterAfter(HttpSecurity httpSecurity, Filter filter,
                                      Class<? extends Filter> afterFilter) {
        httpSecurity.addFilterAfter(filter, afterFilter);
    }

    public static void putFilterBefore(HttpSecurity httpSecurity, Filter filter,
                                       Class<? extends Filter> beforeFilter) {
        httpSecurity.addFilterBefore(filter, beforeFilter);
    }
}
