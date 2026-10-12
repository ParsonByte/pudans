package cn.tolim.pudans.core.user.service;

import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.event.user.UserLoginEvent;
import cn.tolim.pudans.contract.event.user.UserLogoutEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class UserLoginOrLogoutProcessing {

    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public Mono<Void> loginProcessing(String username) {
        return userService
                .getUser(username)
                .doOnNext(user -> {
                    eventPublisher.publishEvent(new UserLoginEvent(this, user));
                })
                .then();
    }

    public Mono<Void> logoutProcessing(String username) {
        return userService
                .getUser(username)
                .doOnNext(user -> {
                    eventPublisher.publishEvent(new UserLogoutEvent(this, user));
                })
                .then();
    }
}
