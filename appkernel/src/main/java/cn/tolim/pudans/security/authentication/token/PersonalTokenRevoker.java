package cn.tolim.pudans.security.authentication.token;

import cn.tolim.pudans.core.user.PersonalTokens;
import cn.tolim.pudans.event.user.PasswordChangedEvent;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 密码变更后吊销个人访问令牌，避免旧令牌在密码重置后继续生效。
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalTokenRevoker {

    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;

    private final PersonalTokens personalTokens;

    // 异步执行，避免阻塞密码变更请求。
    @Async
    @EventListener(PasswordChangedEvent.class)
    public void onPasswordChanged(PasswordChangedEvent event) {
        try {
            personalTokens.revokeAll(event.getUsername()).block(BLOCKING_TIMEOUT);
        } catch (RuntimeException exception) {
            log.warn("密码变更后吊销个人访问令牌失败：{}", event.getUsername(), exception);
        }
    }
}
