package cn.tolim.pudans.core.infra.actuator;

import reactor.core.publisher.Mono;


public interface SiteInfo {


    Mono<GlobalInfo> getGlobalInfo();
}
