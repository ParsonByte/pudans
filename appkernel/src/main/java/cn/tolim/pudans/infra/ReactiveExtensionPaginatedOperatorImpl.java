package cn.tolim.pudans.infra;

import static cn.tolim.pudans.contract.extension.index.query.Queries.isNull;

import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.PageRequest;
import cn.tolim.pudans.contract.extension.PageRequestImpl;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class ReactiveExtensionPaginatedOperatorImpl implements ReactiveExtensionPaginatedOperator {
    private static final int DEFAULT_PAGE_SIZE = 200;
    private final ReactiveExtensionClient client;

    static <E extends Extension> boolean shouldTakeNext(E item, Instant now) {
        var creationTimestamp = item.getMetadata().getCreationTimestamp();
        return creationTimestamp.isBefore(now) || creationTimestamp.equals(now);
    }

    static <E extends Extension> PageRequest nextPage(ListResult<E> result, Sort sort) {
        return PageRequestImpl.of(result.getPage() + 1, result.getSize(), sort);
    }

    @Override
    public <E extends Extension> Mono<Void> deleteContinuously(Class<E> type, ListOptions listOptions) {
        var pageRequest = createPageRequest();
        return cleanupContinuously(type, listOptions, pageRequest);
    }

    private <E extends Extension> Mono<Void> cleanupContinuously(
            Class<E> type, ListOptions listOptions, PageRequest pageRequest) {

        return pageBy(type, listOptions, pageRequest)
                .flatMap(page -> Flux.fromIterable(page.getItems())
                        .flatMap(client::delete)
                        .then(page.hasNext() ? cleanupContinuously(type, listOptions, pageRequest) : Mono.empty()));
    }

    @Override
    public <E extends Extension> Flux<E> deleteInitialBatch(Class<E> type, ListOptions listOptions) {
        var pageRequest = createPageRequest();
        var newFieldQuery = listOptions.getFieldSelector().andQuery(isNull("metadata.deletionTimestamp"));
        listOptions.setFieldSelector(newFieldQuery);
        final Instant now = Instant.now();

        return pageBy(type, listOptions, pageRequest)

                .expand(result -> result.hasNext() ? pageBy(type, listOptions, pageRequest) : Mono.empty())
                .flatMap(result -> Flux.fromIterable(result.getItems()))
                .takeWhile(item -> shouldTakeNext(item, now))
                .flatMap(this::deleteWithRetry);
    }

    @SuppressWarnings("unchecked")
    <E extends Extension> Mono<E> deleteWithRetry(E item) {
        return client.delete(item)
                .onErrorResume(
                        OptimisticLockingFailureException.class,
                        e -> attemptToDelete(
                                (Class<E>) item.getClass(), item.getMetadata().getName()));
    }

    private <E extends Extension> Mono<E> attemptToDelete(Class<E> type, String name) {
        return Mono.defer(() -> client.fetch(type, name).flatMap(client::delete))
                .retryWhen(Retry.backoff(8, Duration.ofMillis(100))
                        .filter(OptimisticLockingFailureException.class::isInstance));
    }

    @Override
    public <E extends Extension> Flux<E> list(Class<E> type, ListOptions listOptions) {
        var pageRequest = createPageRequest();
        return list(type, listOptions, pageRequest);
    }

    private <E extends Extension> Flux<E> list(Class<E> type, ListOptions listOptions, PageRequest pageRequest) {
        final var now = Instant.now();
        return pageBy(type, listOptions, pageRequest)
                .expand(result -> {
                    if (result.hasNext()) {

                        var nextPage = nextPage(result, pageRequest.getSort());
                        return pageBy(type, listOptions, nextPage);
                    } else {
                        return Mono.empty();
                    }
                })
                .flatMap(page -> Flux.fromIterable(page.getItems()))
                .takeWhile(item -> shouldTakeNext(item, now));
    }

    private PageRequest createPageRequest() {
        return PageRequestImpl.of(1, DEFAULT_PAGE_SIZE, Sort.by("metadata.creationTimestamp", "metadata.name"));
    }

    private <E extends Extension> Mono<ListResult<E>> pageBy(
            Class<E> type, ListOptions listOptions, PageRequest pageRequest) {
        return client.listBy(type, listOptions, pageRequest);
    }
}
