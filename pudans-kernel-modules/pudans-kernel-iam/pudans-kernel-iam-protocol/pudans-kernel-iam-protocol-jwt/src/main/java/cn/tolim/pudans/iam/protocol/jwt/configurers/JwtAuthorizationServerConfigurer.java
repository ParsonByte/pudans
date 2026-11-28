
package cn.tolim.pudans.iam.protocol.jwt.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.UnauthorizedAuthenticationEntryPoint;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.jwt.context.JwtAuthorizationServerContextFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getApplicationServiceLoader;


public final class JwtAuthorizationServerConfigurer extends
        AbstractHttpConfigurer<JwtAuthorizationServerConfigurer, HttpSecurity> {
    private final Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> configurers = createConfigurers();


    private final List<EndpointMatcher> endpointMatchers = new ArrayList<>();
    private final UserAgentParser userAgentParser;

    public JwtAuthorizationServerConfigurer(UserAgentParser userAgentParser) {
        this.userAgentParser = userAgentParser;
    }

    public RequestMatcher getEndpointsMatcher() {
        // Return a deferred RequestMatcher
        // since endpointsMatcher is constructed in init(HttpSecurity).
        return request -> new OrRequestMatcher(
                endpointMatchers.stream().map(EndpointMatcher::getRequestMatcher).toList())
                .matches(request);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        this.configurers.values().forEach(configurer -> {
            configurer.init(httpSecurity);
            endpointMatchers.add(configurer.getEndpointMatcher());
        });
        httpSecurity.exceptionHandling(exceptionHandling -> {
            if (exceptionHandling != null) {

                exceptionHandling.authenticationEntryPoint(
                        new UnauthorizedAuthenticationEntryPoint(userAgentParser));
            }
        });

    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        // Authorization server context filter
        ApplicationServiceLoader applicationServiceLoader = getApplicationServiceLoader(httpSecurity);
        JwtAuthorizationServerContextFilter authorizationServerContextFilter = new JwtAuthorizationServerContextFilter(this.endpointMatchers, applicationServiceLoader);
        httpSecurity.addFilterAfter(postProcess(authorizationServerContextFilter), SecurityContextHolderFilter.class);
        this.configurers.values().forEach(configurer -> configurer.configure(httpSecurity));
    }

    private Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> createConfigurers() {
        Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> configurers = new LinkedHashMap<>();
        configurers.put(JwtLoginAuthorizationEndpointConfigurer.class, new JwtLoginAuthorizationEndpointConfigurer(this::postProcess));
        configurers.put(JwtLogoutAuthorizationEndpointConfigurer.class, new JwtLogoutAuthorizationEndpointConfigurer(this::postProcess));
        return configurers;
    }
}
