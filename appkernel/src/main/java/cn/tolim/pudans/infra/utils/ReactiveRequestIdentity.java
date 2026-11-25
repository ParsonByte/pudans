package cn.tolim.pudans.infra.utils;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Component
public class ReactiveRequestIdentity {

    public static final String WORKSPACE_HEADER = "X-Pudans-Workspace";

    // 统一从认证上下文和请求头解析资源归属。
    public Mono<RequestIdentity> resolve(ServerRequest request) {
        var workspaceId = request.headers().firstHeader(WORKSPACE_HEADER);
        if (!StringUtils.hasText(workspaceId)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "request-workspace-missing",
                    "缺少当前工作区请求头。"));
        }
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(context -> context.getAuthentication())
                .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> new RequestIdentity(authentication.getName(), workspaceId.trim()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "request-owner-missing",
                        "当前请求没有已认证用户。")));
    }

    // 从认证上下文提取角色名，供主体级配额解析使用。
    public Mono<Set<String>> roles() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(context -> context.getAuthentication())
                .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> AuthorityUtils.authoritiesToRoles(authentication.getAuthorities()))
                .defaultIfEmpty(Set.of());
    }
}
