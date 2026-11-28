package cn.tolim.pudans.iam.protocol.cas.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.cas.endpoint.CasLogoutEndpointFilter;
import cn.tolim.pudans.iam.protocol.cas.ticket.CasTicketService;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CasEndpointConstants.CAS_LOGOUT_PATH;
import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getApplicationServiceLoader;


public class CasLogoutEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    public CasLogoutEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        requestMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher(CAS_LOGOUT_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(CAS_LOGOUT_PATH, HttpMethod.POST.name()));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        ApplicationServiceLoader applicationServiceLoader = getApplicationServiceLoader(
                httpSecurity);
        CasTicketService casTicketService = httpSecurity.getSharedObject(CasTicketService.class);
        CasLogoutEndpointFilter filter = new CasLogoutEndpointFilter(requestMatcher,
                applicationServiceLoader, casTicketService);
        httpSecurity.addFilterBefore(postProcess(filter), LogoutFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(requestMatcher, false);
    }
}