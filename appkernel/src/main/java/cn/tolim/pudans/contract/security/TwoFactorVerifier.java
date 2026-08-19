package cn.tolim.pudans.contract.security;

import cn.tolim.pudans.contract.core.extension.user.User;
import org.pf4j.ExtensionPoint;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

public interface TwoFactorVerifier extends ExtensionPoint, Ordered {

    String getType();

    Mono<Boolean> verify(User user, String code);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
