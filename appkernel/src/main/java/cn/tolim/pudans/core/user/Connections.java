package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.UserConnection;
import org.springframework.security.oauth2.core.user.OAuth2User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Connections {


    Mono<UserConnection> createConnection(UserConnection connection);


    Mono<UserConnection> createOAuth2Connection(String username, String registrationId, OAuth2User oauth2User);


    Mono<UserConnection> updateUserConnectionIfPresent(String registrationId, OAuth2User oauth2User);


    Flux<UserConnection> listConnections(String username);


    Mono<UserConnection> removeConnection(String username, String connectionName);
}
