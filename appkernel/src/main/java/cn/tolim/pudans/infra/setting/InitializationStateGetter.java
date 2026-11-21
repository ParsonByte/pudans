package cn.tolim.pudans.infra.setting;

import reactor.core.publisher.Mono;


public interface InitializationStateGetter {


    // 判断系统是否仍需完成首次初始化。
    Mono<Boolean> setupRequired();
}
