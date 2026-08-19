package cn.tolim.pudans.contract.security;

import org.pf4j.ExtensionPoint;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

public interface PasswordPolicy extends ExtensionPoint, Ordered {

    Mono<Void> validate(String username, String rawPassword);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
