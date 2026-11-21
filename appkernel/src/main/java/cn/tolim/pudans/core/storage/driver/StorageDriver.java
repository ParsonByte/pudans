package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.contract.storage.StoragePolicyType;
import java.util.Collection;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface StorageDriver extends AutoCloseable {

    // 返回驱动对应的存储类型。
    StoragePolicyType type();

    // 返回驱动支持的存储能力集合。
    Set<StorageCapability> capabilities();

    // 写入对象并返回服务端对象信息。
    Mono<StorageObject> put(StoragePutRequest request);

    // 读取对象内容，可按字节范围请求。
    Mono<StorageReadResult> get(String key, StorageReadOptions options);

    // 查询对象元数据，不读取内容。
    Mono<StorageObject> stat(String key);

    // 批量删除对象，缺失对象应被忽略。
    Mono<Void> delete(Collection<String> keys);

    // 获取对象直链或代理下载信息。
    Mono<StorageSource> source(String key, StorageSourceOptions options);

    // 检测存储连通性并返回结果。
    Mono<StorageTestResult> testConnection();

    // 默认实现无需释放资源。
    @Override
    default void close() {}
}
