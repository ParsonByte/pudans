package cn.tolim.pudans.core.security.authentication.preauth;

import cn.tolim.pudans.core.infra.properties.SecurityProperties.PasswordResetMethod;
import reactor.core.publisher.Flux;


public interface PasswordResetAvailabilityProviders {


    Flux<PasswordResetMethod> getAvailableMethods();
}
