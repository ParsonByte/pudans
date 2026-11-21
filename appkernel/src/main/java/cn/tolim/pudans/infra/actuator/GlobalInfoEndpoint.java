package cn.tolim.pudans.infra.actuator;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@WebEndpoint(id = "globalinfo")
@Component
class GlobalInfoEndpoint {

    private final SiteInfo siteInfo;

    // 注入站点信息提供者，供只读端点查询。
    public GlobalInfoEndpoint(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }

    // 暴露无需认证的全局站点信息读取端点。
    @ReadOperation
    public Mono<GlobalInfo> globalInfo() {
        return siteInfo.getGlobalInfo();
    }
}
