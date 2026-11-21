package cn.tolim.pudans.core.security.authentication;

import cn.tolim.pudans.contract.security.AuthProvider;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface AuthProviders {

    Mono<AuthProvider> enable(String name);

    Mono<AuthProvider> disable(String name);

    Mono<List<ListedAuthProvider>> listAll();


    Flux<AuthProvider> getEnabledProviders();
}
