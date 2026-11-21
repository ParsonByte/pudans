package cn.tolim.pudans.core.user;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public interface UserScopedPatHandler {

    // 创建当前用户的个人访问令牌
    Mono<ServerResponse> create(ServerRequest request);

    // 列出当前用户的个人访问令牌
    Mono<ServerResponse> list(ServerRequest request);

    // 读取当前用户指定名称的令牌
    Mono<ServerResponse> get(ServerRequest request);

    // 撤销当前用户的个人访问令牌
    Mono<ServerResponse> revoke(ServerRequest request);

    // 删除当前用户的个人访问令牌
    Mono<ServerResponse> delete(ServerRequest request);

    // 恢复当前用户已撤销的令牌
    Mono<ServerResponse> restore(ServerRequest request);
}
