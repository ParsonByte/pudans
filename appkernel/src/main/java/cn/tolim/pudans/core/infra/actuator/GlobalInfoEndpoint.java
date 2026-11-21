package cn.tolim.pudans.core.infra.actuator;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@WebEndpoint(id = "globalinfo")
@Component
class GlobalInfoEndpoint {

    private final SiteInfo siteInfo;

    public GlobalInfoEndpoint(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }

    @ReadOperation
    public Mono<GlobalInfo> globalInfo() {
        return siteInfo.getGlobalInfo();
    }
}
