package cn.tolim.pudans.security.authentication.rememberme;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.core.extension.RememberMeToken;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.ReactiveExtensionPaginatedOperatorImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class PersistentRememberMeTokenRepositoryImpl implements PersistentRememberMeTokenRepository {
    private final ReactiveExtensionClient client;
    private final ReactiveExtensionPaginatedOperatorImpl paginatedOperator;

    @Override
    public Mono<RememberMeToken> createNewToken(RememberMeToken token) {
        return client.create(token);
    }

    @Override
    public Mono<RememberMeToken> updateToken(RememberMeToken token) {
        return client.update(token);
    }

    @Override
    public Mono<RememberMeToken> getTokenForSeries(String seriesId) {
        return getTokenExtensionForSeries(seriesId);
    }

    @Override
    public Mono<Void> removeUserTokens(String username) {
        var listOptions =
                ListOptions.builder().andQuery(equal("spec.username", username)).build();
        return paginatedOperator
                .deleteInitialBatch(RememberMeToken.class, listOptions)
                .then();
    }

    @Override
    public Mono<Void> removeToken(String series) {
        return getTokenExtensionForSeries(series).flatMap(client::delete).then();
    }

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
