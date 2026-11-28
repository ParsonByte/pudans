package cn.tolim.pudans.iam.protocol.oidc.configurers;

import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.oidc.endpoint.OidcProviderConfigurationEndpointFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.OidcEndpointConstants.WELL_KNOWN_OPENID_CONFIGURATION;


public final class OidcProviderConfigurationEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    // Restrict for internal use only.
    OidcProviderConfigurationEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        this.requestMatcher = new AntPathRequestMatcher(WELL_KNOWN_OPENID_CONFIGURATION,
                HttpMethod.GET.name());
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        OidcProviderConfigurationEndpointFilter oidcProviderConfigurationEndpointFilter = new OidcProviderConfigurationEndpointFilter(
                this.requestMatcher);
        httpSecurity.addFilterBefore(postProcess(oidcProviderConfigurationEndpointFilter),
                AbstractPreAuthenticatedProcessingFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(this.requestMatcher, false);
    }
}
