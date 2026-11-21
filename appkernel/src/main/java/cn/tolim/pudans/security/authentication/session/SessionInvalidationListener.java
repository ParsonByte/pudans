package cn.tolim.pudans.security.authentication.session;

import cn.tolim.pudans.event.user.PasswordChangedEvent;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class SessionInvalidationListener {

    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;

    private final ReactiveFindByIndexNameSessionRepository<? extends Session> indexedSessionRepository;
    private final ReactiveSessionRepository<? extends Session> sessionRepository;

    // 密码变更后异步清理该用户的全部会话。
    @Async
    @EventListener
    public void onPasswordChanged(PasswordChangedEvent event) {
        String username = event.getUsername();

        invalidateUserSessions(username);
    }

    // 按主体名删除全部会话，使旧登录立即失效。
    private void invalidateUserSessions(String username) {
        indexedSessionRepository
                .findByPrincipalName(username)
                .map(Map::keySet)
                .flatMapMany(Flux::fromIterable)
                .flatMap(sessionRepository::deleteById)
                .then()
                .block(BLOCKING_TIMEOUT);
    }
}
