package cn.tolim.pudans.iam.protocol.saml.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.saml.endpoint.SamlSingleSignOnEndpointFilter;
import cn.tolim.pudans.iam.protocol.saml.service.SamlProtocolService;
import cn.tolim.pudans.iam.protocol.saml.store.SamlAuthnRequestStore;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getApplicationServiceLoader;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SamlEndpointConstants.SAML_SSO_PATH;


public class SamlSingleSignOnEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    public SamlSingleSignOnEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        requestMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher(SAML_SSO_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(SAML_SSO_PATH, HttpMethod.POST.name()));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        ApplicationServiceLoader applicationServiceLoader = getApplicationServiceLoader(
                httpSecurity);
        SamlProtocolService samlProtocolService = httpSecurity
                .getSharedObject(SamlProtocolService.class);
        SamlAuthnRequestStore authnRequestStore = httpSecurity
                .getSharedObject(SamlAuthnRequestStore.class);
        SamlSingleSignOnEndpointFilter filter = new SamlSingleSignOnEndpointFilter(
                requestMatcher, applicationServiceLoader, samlProtocolService, authnRequestStore);
        httpSecurity.addFilterBefore(postProcess(filter), AuthorizationFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(requestMatcher, true);
    }
}