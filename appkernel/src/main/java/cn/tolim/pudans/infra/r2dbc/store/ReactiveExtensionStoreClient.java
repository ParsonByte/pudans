package cn.tolim.pudans.infra.r2dbc.store;

import java.util.List;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveExtensionStoreClient {

    // 按名称前缀列出该前缀下的全部扩展记录。
    Flux<ExtensionStore> listByNamePrefix(String prefix);


    // 按名称游标分页读取指定前缀的记录。
    Flux<ExtensionStore> listBy(String prefix, @Nullable String nameCursor, int limit);


    // 按给定名称批量读取，并保持入参顺序。
    Flux<ExtensionStore> listByNames(List<String> names);

    // 读取单个扩展记录，不存在时返回空。
    Mono<ExtensionStore> fetchByName(String name);

    // 新建扩展记录，名称冲突时转换为重复名称异常。
    Mono<ExtensionStore> create(String name, byte[] data);

    // 以指定版本更新内容，版本不匹配时乐观锁失败。
    Mono<ExtensionStore> update(String name, Long version, byte[] data);

    // 以指定版本删除记录，避免删除并发修改后的数据。
    Mono<ExtensionStore> delete(String name, Long version);
}
