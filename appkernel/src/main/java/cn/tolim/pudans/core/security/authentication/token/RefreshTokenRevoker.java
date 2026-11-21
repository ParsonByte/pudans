package cn.tolim.pudans.core.security.authentication.token;

import cn.tolim.pudans.core.user.RefreshTokens;
import cn.tolim.pudans.event.user.PasswordChangedEvent;
import cn.tolim.pudans.core.infra.utils.ReactiveUtils;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRevoker implements ServerLogoutHandler {

    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;

    private final RefreshTokens refreshTokens;

    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        return revoke(authentication.getName());
    }

    @Async
    @EventListener
    public void onPasswordChanged(PasswordChangedEvent event) {
        try {
            revoke(event.getUsername()).block(BLOCKING_TIMEOUT);
        } catch (RuntimeException exception) {
            log.warn("密码变更后吊销刷新令牌失败：{}", event.getUsername(), exception);
        }
    }

    private Mono<Void> revoke(String username) {
        return refreshTokens.revokeAll(username).then();
    }
}
