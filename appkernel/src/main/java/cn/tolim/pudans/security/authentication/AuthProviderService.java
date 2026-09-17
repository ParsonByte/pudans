package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.contract.core.extension.AuthProvider;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface AuthProviderService {

    Mono<AuthProvider> enable(String name);

    Mono<AuthProvider> disable(String name);

    Mono<List<ListedAuthProvider>> listAll();


    Flux<AuthProvider> getEnabledProviders();
}
