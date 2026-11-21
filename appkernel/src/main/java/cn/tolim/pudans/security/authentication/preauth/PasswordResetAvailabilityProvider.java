package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.properties.SecurityProperties;
import reactor.core.publisher.Mono;


public interface PasswordResetAvailabilityProvider {


    Mono<Boolean> isAvailable(SecurityProperties.PasswordResetMethod method);


    boolean support(String name);
}
