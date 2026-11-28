package cn.tolim.pudans.iam.protocol.cas.configurers;

import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.cas.endpoint.CasServiceValidateEndpointFilter;
import cn.tolim.pudans.iam.protocol.cas.ticket.CasTicketService;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CasEndpointConstants.CAS_P3_PROXY_VALIDATE_PATH;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CasEndpointConstants.CAS_P3_SERVICE_VALIDATE_PATH;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CasEndpointConstants.CAS_PROXY_VALIDATE_PATH;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CasEndpointConstants.CAS_SERVICE_VALIDATE_PATH;


public class CasServiceValidateEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    public CasServiceValidateEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        requestMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher(CAS_SERVICE_VALIDATE_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(CAS_SERVICE_VALIDATE_PATH, HttpMethod.POST.name()),
                new AntPathRequestMatcher(CAS_P3_SERVICE_VALIDATE_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(CAS_P3_SERVICE_VALIDATE_PATH, HttpMethod.POST.name()),
                new AntPathRequestMatcher(CAS_PROXY_VALIDATE_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(CAS_PROXY_VALIDATE_PATH, HttpMethod.POST.name()),
                new AntPathRequestMatcher(CAS_P3_PROXY_VALIDATE_PATH, HttpMethod.GET.name()),
                new AntPathRequestMatcher(CAS_P3_PROXY_VALIDATE_PATH, HttpMethod.POST.name()));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        CasTicketService casTicketService = httpSecurity.getSharedObject(CasTicketService.class);
        CasServiceValidateEndpointFilter filter = new CasServiceValidateEndpointFilter(
                requestMatcher, casTicketService);
        httpSecurity.addFilterBefore(postProcess(filter), AuthorizationFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(requestMatcher, false);
    }
}