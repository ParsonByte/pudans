package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.properties.SecurityProperties.PasswordResetMethod;
import reactor.core.publisher.Flux;


public interface PasswordResetAvailabilityProviders {


    Flux<PasswordResetMethod> getAvailableMethods();


    default Flux<PasswordResetMethod> getOtherAvailableMethods(String methodName) {
        return getAvailableMethods().filter(method -> !method.getName().equals(methodName));
    }
}
