
package cn.tolim.pudans.iam.protocol.jwt.configurers;

import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils;
import cn.tolim.pudans.iam.protocol.jwt.authentication.JwtLogoutAuthenticationProvider;
import cn.tolim.pudans.iam.protocol.jwt.endpoint.JwtLogoutAuthenticationEndpointFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.JwtEndpointConstants.JWT_SLO_PATH;


public class JwtLogoutAuthorizationEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    public JwtLogoutAuthorizationEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }


    @Override
    public void init(HttpSecurity httpSecurity) {
        requestMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher(JWT_SLO_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(JWT_SLO_PATH, HttpMethod.POST.name()));
        httpSecurity.authenticationProvider(new JwtLogoutAuthenticationProvider());
    }


    @Override
    public void configure(HttpSecurity httpSecurity) {
        AuthenticationManager authenticationManager = httpSecurity
                .getSharedObject(AuthenticationManager.class);
        JwtLogoutAuthenticationEndpointFilter logoutAuthenticationEndpointFilter = new JwtLogoutAuthenticationEndpointFilter(
                requestMatcher, authenticationManager);
        logoutAuthenticationEndpointFilter.setAuthenticationDetailsSource(
                AuthenticationUtils.getAuthenticationDetailsSource(httpSecurity));
        httpSecurity.addFilterBefore(postProcess(logoutAuthenticationEndpointFilter),
                LogoutFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(this.requestMatcher, false);
    }
}
