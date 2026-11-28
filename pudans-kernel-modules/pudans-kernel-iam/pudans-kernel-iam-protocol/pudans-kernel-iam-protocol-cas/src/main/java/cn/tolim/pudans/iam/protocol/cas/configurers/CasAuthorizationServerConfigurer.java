package cn.tolim.pudans.iam.protocol.cas.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import cn.tolim.pudans.iam.protocol.cas.context.CasAuthorizationServerContextFilter;
import cn.tolim.pudans.iam.protocol.cas.ticket.CasTicketService;
import cn.tolim.pudans.iam.protocol.cas.ticket.DefaultCasTicketService;
import cn.tolim.pudans.iam.protocol.cas.ticket.InMemoryCasTicketRegistry;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.UnauthorizedAuthenticationEntryPoint;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
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


public final class CasAuthorizationServerConfigurer extends
        AbstractHttpConfigurer<CasAuthorizationServerConfigurer, HttpSecurity> {

    private final Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> configurers =
            createConfigurers();
    private final List<EndpointMatcher> endpointMatchers = new ArrayList<>();
    private final UserAgentParser userAgentParser;

    private CasTicketService casTicketService;

    public CasAuthorizationServerConfigurer(UserAgentParser userAgentParser) {
        this.userAgentParser = userAgentParser;
    }

    // Set a custom ticket service, for example a Redis-backed ticket registry.
    public CasAuthorizationServerConfigurer setCasTicketService(
            CasTicketService casTicketService) {
        this.casTicketService = casTicketService;
        return this;
    }

    public RequestMatcher getEndpointsMatcher() {
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
        httpSecurity.exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(new UnauthorizedAuthenticationEntryPoint(
                        userAgentParser)));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        ApplicationServiceLoader applicationServiceLoader = getApplicationServiceLoader(
                httpSecurity);
        CasTicketService ticketService = casTicketService == null
                ? new DefaultCasTicketService(new InMemoryCasTicketRegistry(),
                applicationServiceLoader)
                : casTicketService;
        httpSecurity.setSharedObject(CasTicketService.class, ticketService);
        CasAuthorizationServerContextFilter contextFilter =
                new CasAuthorizationServerContextFilter(endpointMatchers,
                        applicationServiceLoader);
        httpSecurity.addFilterAfter(postProcess(contextFilter),
                SecurityContextHolderFilter.class);
        this.configurers.values().forEach(configurer -> configurer.configure(httpSecurity));
    }

    private Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> createConfigurers() {
        Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> result =
                new LinkedHashMap<>();
        result.put(CasLoginEndpointConfigurer.class,
                new CasLoginEndpointConfigurer(this::postProcess));
        result.put(CasLogoutEndpointConfigurer.class,
                new CasLogoutEndpointConfigurer(this::postProcess));
        result.put(CasServiceValidateEndpointConfigurer.class,
                new CasServiceValidateEndpointConfigurer(this::postProcess));
        result.put(CasValidateEndpointConfigurer.class,
                new CasValidateEndpointConfigurer(this::postProcess));
        return result;
    }
}