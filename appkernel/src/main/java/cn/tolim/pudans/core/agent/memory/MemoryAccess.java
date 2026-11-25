package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryAclRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryAccess {

    private final MemoryAclRepository acl;

    public Mono<Void> require(
            RequestIdentity identity, Collection<String> roles, String permission) {
        var roleChecks = roles == null
                ? Flux.<Boolean>empty()
                : Flux.fromIterable(roles)
                        .concatMap(role -> acl.exists("role", role, permission));
        return Flux.concat(
                        acl.exists("user", identity.ownerId(), permission),
                        roleChecks)
                .any(Boolean::booleanValue)
                .flatMap(allowed -> Boolean.TRUE.equals(allowed)
                        ? Mono.empty()
                        : Mono.error(new ApiException(
                                HttpStatus.FORBIDDEN,
                                "memory-review-forbidden",
                                "当前身份没有记忆池审核权限。")));
    }
}
