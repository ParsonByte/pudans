package cn.tolim.pudans.infra.actuator;

import reactor.core.publisher.Mono;


public interface SiteInfo {


    // 返回面板所需的全局站点信息。
    Mono<GlobalInfo> getGlobalInfo();
}
