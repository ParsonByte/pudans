package cn.tolim.pudans.security.authentication.rememberme;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;

import cn.tolim.pudans.security.contract.RememberMeToken;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.ReactiveExtensionPaginatedOperatorImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class PersistentRememberMeTokenRepositoryImpl implements PersistentRememberMeTokenRepository {
    private final ReactiveExtensionClient client;
    private final ReactiveExtensionPaginatedOperatorImpl paginatedOperator;

    // 通过扩展客户端持久化新令牌，复用统一存储能力。
    @Override
    public Mono<RememberMeToken> createNewToken(RememberMeToken token) {
        return client.create(token);
    }

    // 更新令牌资源，同时写入新令牌值与最近使用时间。
    @Override
    public Mono<RememberMeToken> updateToken(RememberMeToken token) {
        return client.update(token);
    }

    // 复用内部查询，按序列定位唯一有效令牌。
    @Override
    public Mono<RememberMeToken> getTokenForSeries(String seriesId) {
        return getTokenExtensionForSeries(seriesId);
    }

    // 按用户名批量删除，覆盖该用户全部持久登录。
    @Override
    public Mono<Void> removeUserTokens(String username) {
        var listOptions =
                ListOptions.builder().andQuery(equal("spec.username", username)).build();
        return paginatedOperator
                .deleteInitialBatch(RememberMeToken.class, listOptions)
                .then();
    }

    // 先定位令牌再删除，避免误删最新记录。
    @Override
    public Mono<Void> removeToken(String series) {
        return getTokenExtensionForSeries(series).flatMap(client::delete).then();
    }

    // 只匹配未删除记录，并取排序最前的一条。
    private Mono<RememberMeToken> getTokenExtensionForSeries(String seriesId) {
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.series", seriesId))
                .andQuery(ExtensionUtil.notDeleting())
                .build();
        return client.listTopNames(RememberMeToken.class, listOptions, defaultSort(), 1)
                .next()
                .flatMap(name -> client.fetch(RememberMeToken.class, name));
    }
}
