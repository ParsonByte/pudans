package cn.tolim.pudans.infra.r2dbc.store;

import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
public interface ExtensionStoreRepository extends R2dbcRepository<ExtensionStore, String> {


    Flux<ExtensionStore> findAllByNameStartingWith(String prefix);

    Flux<ExtensionStore> findAllByNameStartingWith(String prefix, Pageable pageable);

    Mono<Long> countByNameStartingWith(String prefix);


    Flux<ExtensionStore> findByNameIn(Collection<String> names);

    Flux<ExtensionStore> findAllByNameStartingWithAndNameGreaterThan(
            String prefix, String nameCursor, Pageable pageable);
}
