package cn.tolim.pudans.core.rbac;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.ReactiveSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 集中失效用户会话，供角色、用户组和成员绑定变更后立即生效。
@Component
@RequiredArgsConstructor
public class SessionInvalidator {

    private final ReactiveSessionRegistry sessionRegistry;

    // 失效单个用户的全部会话。
    public Mono<Void> invalidate(String username) {
        return invalidateAll(java.util.List.of(username));
    }

    // 失效一组用户的全部会话并跳过空用户名。
    public Mono<Void> invalidateAll(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(usernames)
                .filter(StringUtils::hasText)
                .distinct()
                .flatMap(sessionRegistry::getAllSessions)
                .flatMap(session -> session.invalidate())
                .then();
    }
}
