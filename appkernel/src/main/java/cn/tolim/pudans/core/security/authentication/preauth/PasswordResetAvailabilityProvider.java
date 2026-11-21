package cn.tolim.pudans.core.security.authentication.preauth;

import cn.tolim.pudans.core.infra.properties.SecurityProperties;
import reactor.core.publisher.Mono;


public interface PasswordResetAvailabilityProvider {


    Mono<Boolean> isAvailable(SecurityProperties.PasswordResetMethod method);


    boolean support(String name);
}
