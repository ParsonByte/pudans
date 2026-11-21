package cn.tolim.pudans.core.user;

import cn.tolim.pudans.security.api.PersonalAccessToken;
import reactor.core.publisher.Mono;


public interface PersonalTokens {


    Mono<PersonalAccessToken> create(PersonalAccessToken patRequest);


    Mono<PersonalAccessToken> create(PersonalAccessToken patRequest, String username);


    Mono<PersonalAccessToken> revoke(String patName, String username);


    Mono<PersonalAccessToken> restore(String patName, String username);


    Mono<PersonalAccessToken> delete(String patName, String username);


    Mono<PersonalAccessToken> get(String patName, String username);


    Mono<String> generateToken(PersonalAccessToken pat);
}
