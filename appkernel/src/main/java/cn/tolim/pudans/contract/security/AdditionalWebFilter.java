package cn.tolim.pudans.contract.security;

import org.pf4j.ExtensionPoint;
import org.springframework.core.Ordered;
import org.springframework.web.server.WebFilter;


public interface AdditionalWebFilter extends WebFilter, ExtensionPoint, Ordered {


    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
