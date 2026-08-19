package cn.tolim.pudans.contract.user;

import cn.tolim.pudans.contract.core.extension.user.User;
import org.pf4j.ExtensionPoint;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

public interface UserProvisioningHook extends ExtensionPoint, Ordered {

    Mono<Void> beforeCreate(User user);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
