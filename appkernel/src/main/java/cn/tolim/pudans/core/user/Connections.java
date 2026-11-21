package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.UserConnection;
import org.springframework.security.oauth2.core.user.OAuth2User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Connections {


    Mono<UserConnection> createUserConnection(String username, String registrationId, OAuth2User oauth2User);


    Mono<UserConnection> updateUserConnectionIfPresent(String registrationId, OAuth2User oauth2User);


    Flux<UserConnection> removeUserConnection(String registrationId, String username);


    Mono<UserConnection> getByProviderUserId(String registrationId, String providerUserId);


    Flux<UserConnection> listConnections(String username);


    Mono<Void> removeByProviderUserId(String registrationId, String providerUserId);
}
