package cn.tolim.pudans.core.user;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.event.user.UserLoginEvent;
import cn.tolim.pudans.event.user.UserLogoutEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class UserLoginOrLogoutProcessing {

    private final Accounts accounts;
    private final ApplicationEventPublisher eventPublisher;

    // 读取用户并发布登录事件
    public Mono<Void> loginProcessing(String username) {
        return accounts
                .getUser(username)
                .doOnNext(user -> {
                    eventPublisher.publishEvent(new UserLoginEvent(this, user));
                })
                .then();
    }

    // 读取用户并发布登出事件
    public Mono<Void> logoutProcessing(String username) {
        return accounts
                .getUser(username)
                .doOnNext(user -> {
                    eventPublisher.publishEvent(new UserLogoutEvent(this, user));
                })
                .then();
    }
}
