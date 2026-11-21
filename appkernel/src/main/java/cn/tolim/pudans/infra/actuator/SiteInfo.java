package cn.tolim.pudans.infra.actuator;

import reactor.core.publisher.Mono;


public interface SiteInfo {


    Mono<GlobalInfo> getGlobalInfo();
}
