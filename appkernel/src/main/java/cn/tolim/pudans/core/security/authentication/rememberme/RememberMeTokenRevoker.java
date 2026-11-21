package cn.tolim.pudans.core.security.authentication.rememberme;

import cn.tolim.pudans.event.user.PasswordChangedEvent;
import cn.tolim.pudans.core.infra.utils.ReactiveUtils;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class RememberMeTokenRevoker {
    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;
    private final PersistentRememberMeTokenRepository tokenRepository;

    @Async
    @EventListener(PasswordChangedEvent.class)
    public void onPasswordChanged(PasswordChangedEvent event) {
        tokenRepository.removeUserTokens(event.getUsername()).block(BLOCKING_TIMEOUT);
    }
}
