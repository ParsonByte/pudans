package cn.tolim.pudans.infra.setting;

import reactor.core.publisher.Mono;


public interface InitializationStateGetter {


    Mono<Boolean> setupRequired();
}
