package cn.tolim.pudans.infra.r2dbc.store;

import cn.tolim.pudans.infra.exception.DuplicateNameException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ReactiveExtensionStoreClientImpl implements ReactiveExtensionStoreClient {

    private static final int DEFAULT_FETCH_SIZE = 100;

    private final ExtensionStoreRepository repository;

    private final R2dbcEntityOperations entityOperations;

    private int fetchSize = DEFAULT_FETCH_SIZE;

    // 自动补全前缀分隔符后按名称升序读取。
    @Override
    public Flux<ExtensionStore> listByNamePrefix(String prefix) {
        Assert.hasText(prefix, "前缀不能为空");

        prefix = Strings.CS.appendIfMissing(prefix, "/");
        return entityOperations
                .select(ExtensionStore.class)
                .withFetchSize(fetchSize)
                .matching(Query.query(Criteria.where("name").like(prefix + "%"))
                        .sort(Sort.by(Sort.Direction.ASC, "name")))
                .all();
    }

    // 使用名称游标实现稳定的分页读取。
    @Override
    public Flux<ExtensionStore> listBy(String prefix, @Nullable String nameCursor, int limit) {
        Assert.hasText(prefix, "前缀不能为空");
        Assert.isTrue(limit > 0, "limit 必须大于 0");

        prefix = Strings.CS.appendIfMissing(prefix, "/");
        var criteria = Criteria.where("name").like(prefix + "%");
        if (StringUtils.isNotBlank(nameCursor)) {
            nameCursor = Strings.CS.prependIfMissing(nameCursor, prefix);
            criteria = criteria.and(Criteria.where("name").greaterThan(nameCursor));
        }
        var q = Query.query(criteria).sort(Sort.by(Sort.Direction.ASC, "name"));
        return entityOperations
                .select(ExtensionStore.class)
                .matching(q.limit(limit))
                .all();
    }

    // 查询后按入参顺序重排，保持调用方预期。
    @Override
    public Flux<ExtensionStore> listByNames(List<String> names) {
        if (CollectionUtils.isEmpty(names)) {
            return Flux.empty();
        }

        var orderMap = IntStream.range(0, names.size())
                .boxed()
                .collect(Collectors.toMap(names::get, Function.identity(), (a, b) -> a));
        return repository.findByNameIn(names).sort(Comparator.comparingInt(es -> orderMap.get(es.getName())));
    }

    // 按名称读取扩展记录。
    @Override
    public Mono<ExtensionStore> fetchByName(String name) {
        return repository.findById(name);
    }

    // 新建记录，并将唯一键冲突映射为重复名称。
    @Override
    public Mono<ExtensionStore> create(String name, byte[] data) {
        return repository
                .save(new ExtensionStore(name, data))
                .onErrorMap(
                        DuplicateKeyException.class, t -> new DuplicateNameException("检测到重复名称。", t));
    }

    // 使用乐观锁版本保存更新内容。
    @Override
    public Mono<ExtensionStore> update(String name, Long version, byte[] data) {
        return repository.save(new ExtensionStore(name, data, version));
    }

    // 先读取再带版本删除，触发乐观锁校验。
    @Override
    public Mono<ExtensionStore> delete(String name, Long version) {
        return repository.findById(name).flatMap(extensionStore -> {

            extensionStore.setVersion(version);
            return repository.delete(extensionStore).thenReturn(extensionStore);
        });
    }
}
