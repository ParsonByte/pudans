package cn.tolim.pudans.iam.protocol.saml.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.saml.endpoint.SamlLogoutEndpointFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getApplicationServiceLoader;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SamlEndpointConstants.SAML_SLO_PATH;


public class SamlLogoutEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    public SamlLogoutEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        requestMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher(SAML_SLO_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(SAML_SLO_PATH, HttpMethod.POST.name()));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        ApplicationServiceLoader applicationServiceLoader = getApplicationServiceLoader(
                httpSecurity);
        SamlLogoutEndpointFilter filter = new SamlLogoutEndpointFilter(requestMatcher,
                applicationServiceLoader);
        httpSecurity.addFilterBefore(postProcess(filter), LogoutFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(requestMatcher, false);
    }
}