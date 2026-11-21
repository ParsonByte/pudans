package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.security.api.RememberMeToken;
import reactor.core.publisher.Mono;

public interface PersistentRememberMeTokenRepository {

    Mono<RememberMeToken> createNewToken(RememberMeToken token);


    Mono<RememberMeToken> updateToken(RememberMeToken token);

    Mono<RememberMeToken> getTokenForSeries(String seriesId);

    Mono<Void> removeUserTokens(String username);

    Mono<Void> removeToken(String series);
}
