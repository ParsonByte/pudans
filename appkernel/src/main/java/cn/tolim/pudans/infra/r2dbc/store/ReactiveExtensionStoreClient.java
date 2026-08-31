package cn.tolim.pudans.infra.r2dbc.store;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveExtensionStoreClient {

    Flux<ExtensionStore> listByNamePrefix(String prefix);

    Mono<Page<ExtensionStore>> listByNamePrefix(String prefix, Pageable pageable);


    Flux<ExtensionStore> listBy(String prefix, @Nullable String nameCursor, int limit);

    Mono<Long> countByNamePrefix(String prefix);


    Flux<ExtensionStore> listByNames(List<String> names);

    Mono<ExtensionStore> fetchByName(String name);

    Mono<ExtensionStore> create(String name, byte[] data);

    Mono<ExtensionStore> update(String name, Long version, byte[] data);

    Mono<ExtensionStore> delete(String name, Long version);
}
