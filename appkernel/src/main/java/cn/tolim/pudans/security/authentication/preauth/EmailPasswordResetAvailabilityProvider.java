package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.properties.SecurityProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
public class EmailPasswordResetAvailabilityProvider implements PasswordResetAvailabilityProvider {

    @Override
    public Mono<Boolean> isAvailable(SecurityProperties.PasswordResetMethod method) {

        return Mono.just(true);
    }

    @Override
    public boolean support(String name) {
        return "email".equals(name);
    }
}
