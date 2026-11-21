package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.security.contract.AuthProvider;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface AuthProviders {

    // 启用指定登录提供方，配置不可用时拒绝操作。
    Mono<AuthProvider> enable(String name);

    // 停用指定登录提供方，特权与参数托管的提供方不允许停用。
    Mono<AuthProvider> disable(String name);

    // 汇总提供方配置、当前用户绑定与启停状态。
    Mono<List<ListedAuthProvider>> listAll();


    // 返回已启用的提供方，并按配置的优先级排序。
    Flux<AuthProvider> getEnabledProviders();
}
