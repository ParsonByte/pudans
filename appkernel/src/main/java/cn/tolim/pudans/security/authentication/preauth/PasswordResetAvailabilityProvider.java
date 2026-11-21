package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.properties.SecurityProperties;
import reactor.core.publisher.Mono;


public interface PasswordResetAvailabilityProvider {


    // 判断指定重置方式当前是否可对外提供。
    Mono<Boolean> isAvailable(SecurityProperties.PasswordResetMethod method);


    // 声明该提供者负责的重置方式名称。
    boolean support(String name);
}
