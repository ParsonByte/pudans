package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.UserConnection;
import org.springframework.security.oauth2.core.user.OAuth2User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Connections {


    // 建立身份绑定，重复绑定同一身份时拒绝
    Mono<UserConnection> createConnection(UserConnection connection);


    // 用外部用户资料创建身份绑定
    Mono<UserConnection> createOAuth2Connection(String username, String registrationId, OAuth2User oauth2User);


    // 已存在连接时刷新资料，否则返回空
    Mono<UserConnection> updateUserConnectionIfPresent(String registrationId, OAuth2User oauth2User);


    // 列出用户未删除的外部身份绑定
    Flux<UserConnection> listConnections(String username);


    // 校验归属后移除绑定并保护唯一登录方式
    Mono<UserConnection> removeConnection(String username, String connectionName);
}
