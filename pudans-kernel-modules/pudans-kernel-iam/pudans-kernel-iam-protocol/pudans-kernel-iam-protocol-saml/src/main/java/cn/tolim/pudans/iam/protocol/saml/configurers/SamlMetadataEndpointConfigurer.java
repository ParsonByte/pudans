package cn.tolim.pudans.iam.protocol.saml.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.saml.endpoint.SamlMetadataEndpointFilter;
import cn.tolim.pudans.iam.protocol.saml.service.SamlProtocolService;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getApplicationServiceLoader;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SamlEndpointConstants.SAML_METADATA_PATH;


public class SamlMetadataEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    public SamlMetadataEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        requestMatcher = new AntPathRequestMatcher(SAML_METADATA_PATH,
                HttpMethod.GET.name());
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        ApplicationServiceLoader applicationServiceLoader = getApplicationServiceLoader(
                httpSecurity);
        SamlProtocolService samlProtocolService = httpSecurity
                .getSharedObject(SamlProtocolService.class);
        SamlMetadataEndpointFilter filter = new SamlMetadataEndpointFilter(requestMatcher,
                applicationServiceLoader, samlProtocolService);
        httpSecurity.addFilterBefore(postProcess(filter), AuthorizationFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(requestMatcher, false);
    }
}