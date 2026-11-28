package cn.tolim.pudans.iam.protocol.saml.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.UnauthorizedAuthenticationEntryPoint;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.saml.context.SamlAuthorizationServerContextFilter;
import cn.tolim.pudans.iam.protocol.saml.service.DefaultSamlProtocolService;
import cn.tolim.pudans.iam.protocol.saml.service.PortalSamlEndpointResolver;
import cn.tolim.pudans.iam.protocol.saml.service.SamlProtocolService;
import cn.tolim.pudans.iam.protocol.saml.signature.JdkSamlResponseSigner;
import cn.tolim.pudans.iam.protocol.saml.store.InMemorySamlAuthnRequestStore;
import cn.tolim.pudans.iam.protocol.saml.store.SamlAuthnRequestStore;
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


public final class SamlAuthorizationServerConfigurer extends
        AbstractHttpConfigurer<SamlAuthorizationServerConfigurer, HttpSecurity> {

    private final Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> configurers =
            createConfigurers();
    private final List<EndpointMatcher> endpointMatchers = new ArrayList<>();
    private final UserAgentParser userAgentParser;

    private SamlProtocolService samlProtocolService;
    private SamlAuthnRequestStore authnRequestStore;

    public SamlAuthorizationServerConfigurer(UserAgentParser userAgentParser) {
        this.userAgentParser = userAgentParser;
    }

    // Set a custom SAML protocol service, for example one backed by an external SAML library.
    public SamlAuthorizationServerConfigurer setSamlProtocolService(
            SamlProtocolService samlProtocolService) {
        this.samlProtocolService = samlProtocolService;
        return this;
    }

    // Set a custom authn request store, for example a Redis-backed store.
    public SamlAuthorizationServerConfigurer setAuthnRequestStore(
            SamlAuthnRequestStore authnRequestStore) {
        this.authnRequestStore = authnRequestStore;
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
        SamlProtocolService protocolService = samlProtocolService == null
                ? new DefaultSamlProtocolService(new JdkSamlResponseSigner(),
                new PortalSamlEndpointResolver())
                : samlProtocolService;
        SamlAuthnRequestStore requestStore = authnRequestStore == null
                ? new InMemorySamlAuthnRequestStore() : authnRequestStore;
        httpSecurity.setSharedObject(SamlProtocolService.class, protocolService);
        httpSecurity.setSharedObject(SamlAuthnRequestStore.class, requestStore);
        SamlAuthorizationServerContextFilter contextFilter =
                new SamlAuthorizationServerContextFilter(endpointMatchers,
                        applicationServiceLoader);
        httpSecurity.addFilterAfter(postProcess(contextFilter),
                SecurityContextHolderFilter.class);
        this.configurers.values().forEach(configurer -> configurer.configure(httpSecurity));
    }

    private Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> createConfigurers() {
        Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> result =
                new LinkedHashMap<>();
        result.put(SamlMetadataEndpointConfigurer.class,
                new SamlMetadataEndpointConfigurer(this::postProcess));
        result.put(SamlSingleSignOnEndpointConfigurer.class,
                new SamlSingleSignOnEndpointConfigurer(this::postProcess));
        result.put(SamlLogoutEndpointConfigurer.class,
                new SamlLogoutEndpointConfigurer(this::postProcess));
        return result;
    }
}