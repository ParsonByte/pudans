
package cn.tolim.pudans.iam.protocol.jwt.configurers;

import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils;
import cn.tolim.pudans.iam.protocol.jwt.JwtAuthorizationService;
import cn.tolim.pudans.iam.protocol.jwt.authentication.JwtLoginAuthenticationTokenProvider;
import cn.tolim.pudans.iam.protocol.jwt.endpoint.JwtLoginAuthenticationEndpointFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.JwtEndpointConstants.IDP_JWT_SSO_INITIATOR;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.JwtEndpointConstants.JWT_SSO_PATH;


public class JwtLoginAuthorizationEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    public JwtLoginAuthorizationEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }


    @Override
    public void init(HttpSecurity httpSecurity) {
        JwtAuthorizationService authorizationService = JwtAuthenticationUtils
                .getAuthorizationService(httpSecurity);
        requestMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher(IDP_JWT_SSO_INITIATOR, HttpMethod.POST.name()),
                new AntPathRequestMatcher(IDP_JWT_SSO_INITIATOR, HttpMethod.GET.name()),
                new AntPathRequestMatcher(JWT_SSO_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(JWT_SSO_PATH, HttpMethod.POST.name()));
        httpSecurity
                .authenticationProvider(new JwtLoginAuthenticationTokenProvider(authorizationService));
    }


    @Override
    public void configure(HttpSecurity httpSecurity) {
        AuthenticationManager authenticationManager = httpSecurity
                .getSharedObject(AuthenticationManager.class);
        JwtLoginAuthenticationEndpointFilter jwtLoginAuthenticationEndpointFilter = new JwtLoginAuthenticationEndpointFilter(
                requestMatcher, authenticationManager);
        jwtLoginAuthenticationEndpointFilter.setAuthenticationDetailsSource(
                AuthenticationUtils.getAuthenticationDetailsSource(httpSecurity));
        httpSecurity.addFilterBefore(postProcess(jwtLoginAuthenticationEndpointFilter),
                AuthorizationFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(this.requestMatcher, true);
    }
}
