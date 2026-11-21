package cn.tolim.pudans.infra.r2dbc.store;

import java.util.Collection;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;


@Repository
public interface ExtensionStoreRepository extends R2dbcRepository<ExtensionStore, String> {

    // 批量按名称查询扩展存储记录。
    Flux<ExtensionStore> findByNameIn(Collection<String> names);
}
