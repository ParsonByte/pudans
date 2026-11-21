package cn.tolim.pudans.core.infra.setting;

import reactor.core.publisher.Mono;


public interface InitializationStateGetter {


    Mono<Boolean> setupRequired();
}
