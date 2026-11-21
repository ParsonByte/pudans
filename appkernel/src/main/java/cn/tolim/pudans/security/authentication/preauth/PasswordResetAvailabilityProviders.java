package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.properties.SecurityProperties.PasswordResetMethod;
import reactor.core.publisher.Flux;


public interface PasswordResetAvailabilityProviders {


    // 汇总当前真正可用的密码重置方式。
    Flux<PasswordResetMethod> getAvailableMethods();
}
