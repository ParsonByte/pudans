package cn.tolim.pudans.extension;

import static org.apache.commons.lang3.RandomStringUtils.secure;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Predicates;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.extension.index.IndexEngine;
import cn.tolim.pudans.extension.contract.index.IndexedQueryEngine;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.infra.r2dbc.store.ReactiveExtensionStoreClient;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.SchemeManager;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.PageRequest;
import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.ReservedLabels;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.extension.contract.JsonExtension;
import cn.tolim.pudans.extension.contract.Watcher;

@Slf4j
@Component
public class ReactiveExtensionClientImpl implements InternalReactiveExtensionClient, DisposableBean {

    public static final int GENERATE_NAME_RANDOM_LENGTH = 8;

    private static final int LIST_ALL_BATCH_SIZE = 1000;

    private final ReactiveExtensionStoreClient client;

    private final ExtensionConverter converter;

    private final SchemeManager schemeManager;

    private final Watcher.WatcherComposite watchers = new Watcher.WatcherComposite();

    private final ObjectMapper objectMapper;

    private final IndexEngine indexEngine;

    private Scheduler scheduler;

    
    
    
    private Scheduler queryScheduler;

    private TransactionalOperator transactionalOperator;

    // 装配依赖并创建事务操作器与查询调度器。
    public ReactiveExtensionClientImpl(
            ReactiveExtensionStoreClient client,
            ExtensionConverter converter,
            SchemeManager schemeManager,
            ObjectMapper objectMapper,
            IndexEngine indexEngine,
            ReactiveTransactionManager reactiveTransactionManager) {
        this.client = client;
        this.converter = converter;
        this.schemeManager = schemeManager;
        this.objectMapper = objectMapper;
        this.indexEngine = indexEngine;
        this.transactionalOperator = TransactionalOperator.create(reactiveTransactionManager);
        this.scheduler = Schedulers.boundedElastic();
        this.queryScheduler = Schedulers.newBoundedElastic(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
                "extension-query");
    }

    // 释放查询调度器以免线程泄漏。
    @Override
    public void destroy() {
        this.queryScheduler.dispose();
    }

    

    // 替换调度器供测试控制异步执行。
    void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }


    // 替换事务操作器以适配测试事务。
    void setTransactionalOperator(TransactionalOperator transactionalOperator) {
        this.transactionalOperator = transactionalOperator;
    }

    // 按存储前缀读取并过滤排序扩展。
    @Override
    public <E extends Extension> Flux<E> list(Class<E> type, Predicate<E> predicate, Comparator<E> comparator) {
        var scheme = schemeManager.get(type);
        var prefix = ExtensionStoreUtil.buildStoreNamePrefix(scheme);

        return client.listByNamePrefix(prefix)
                .map(extensionStore -> converter.convertFrom(type, extensionStore))
                .filter(predicate == null ? Predicates.isTrue() : predicate)
                .sort(comparator == null ? Comparator.naturalOrder() : comparator);
    }

    // 经索引取键后分批读取全部扩展。
    @Override
    public <E extends Extension> Flux<E> listAll(Class<E> type, ListOptions options, Sort sort) {
        var nullSafeSort = Optional.ofNullable(sort).orElseGet(() -> {
            log.warn("排序参数为空，建议改用 Sort.unsorted()；对 null 的兼容支持将在后续版本中移除。");
            return Sort.unsorted();
        });
        var scheme = schemeManager.get(type);

        return Mono.fromCallable(() -> indexEngine.retrieveAll(scheme.type(), options, nullSafeSort))
                .subscribeOn(this.queryScheduler)
                .flatMapMany(objectKeys -> {
                    var storeNames = StreamSupport.stream(objectKeys.spliterator(), false)
                            .map(objectKey -> ExtensionStoreUtil.buildStoreName(scheme, objectKey))
                            .toList();
                    if (log.isDebugEnabled()) {
                        if (storeNames.size() > 500) {
                            log.warn("""
                                通过 listAll 获取的对象数量过多（{}），\
                                建议使用分页查询。\
                                """, storeNames.size());
                        }
                    }
                    long startTimeMs = System.currentTimeMillis();
                    return Flux.fromIterable(storeNames)
                            .buffer(LIST_ALL_BATCH_SIZE)
                            .concatMap(batch -> client.listByNames(batch)
                                    .map(extensionStore -> converter.convertFrom(type, extensionStore)))
                            .doOnComplete(() -> log.debug(
                                    "已从数据库按名称成功检索 {} 的全部数据，耗时 {} 毫秒",
                                    scheme.groupVersionKind(),
                                    System.currentTimeMillis() - startTimeMs));
                });
    }

    // 经索引直接返回全部扩展名称。
    @Override
    public <E extends Extension> Flux<String> listAllNames(Class<E> type, ListOptions options, Sort sort) {
        var scheme = schemeManager.get(type);
        return Mono.fromCallable(() -> indexEngine.retrieveAll(scheme.type(), options, sort))
                .subscribeOn(this.queryScheduler)
                .flatMapMany(Flux::fromIterable);
    }

    // 经索引返回排序靠前的指定数量名称。
    @Override
    public <E extends Extension> Flux<String> listTopNames(Class<E> type, ListOptions options, Sort sort, int topN) {
        var scheme = schemeManager.get(type);
        return Mono.fromCallable(() -> indexEngine.retrieveTopN(scheme.type(), options, sort, topN))
                .subscribeOn(this.queryScheduler)
                .flatMapMany(Flux::fromIterable);
    }

    // 分页读取扩展并附带回填总数。
    @Override
    public <E extends Extension> Mono<ListResult<E>> listBy(Class<E> type, ListOptions options, PageRequest page) {
        var scheme = schemeManager.get(type);
        return Mono.fromCallable(() -> indexEngine.retrieve(scheme.type(), options, page))
                .subscribeOn(this.queryScheduler)
                .flatMap(listResult -> {
                    var storeNames = listResult
                            .get()
                            .map(objectKey -> ExtensionStoreUtil.buildStoreName(scheme, objectKey))
                            .toList();
                    final long startTimeMs = System.currentTimeMillis();
                    return client.listByNames(storeNames)
                            .map(extensionStore -> converter.convertFrom(type, extensionStore))
                            .doOnComplete(() -> log.debug(
                                    "已从数据库按名称成功检索 {}，耗时 {} 毫秒",
                                    scheme.groupVersionKind(),
                                    System.currentTimeMillis() - startTimeMs))
                            .collectList()
                            .map(items -> new ListResult<>(
                                    page.getPageNumber(), page.getPageSize(), listResult.getTotal(), items));
                })
                .defaultIfEmpty(ListResult.emptyResult());
    }

    // 分页读取扩展名称而不加载实体。
    @Override
    public <E extends Extension> Mono<ListResult<String>> listNamesBy(
            Class<E> type, ListOptions options, PageRequest pageable) {
        var scheme = schemeManager.get(type);
        return Mono.fromCallable(() -> indexEngine.retrieve(scheme.type(), options, pageable))
                .subscribeOn(this.queryScheduler);
    }

    // 经索引统计满足条件的扩展数量。
    @Override
    public <E extends Extension> Mono<Long> countBy(Class<E> type, ListOptions options) {
        var scheme = schemeManager.get(type);
        return Mono.fromCallable(() -> indexEngine.count(scheme.type(), options))
                .subscribeOn(this.queryScheduler);
    }

    // 按名称读取扩展，不存在时返回空。
    @Override
    public <E extends Extension> Mono<E> fetch(Class<E> type, String name) {
        var storeName = ExtensionStoreUtil.buildStoreName(schemeManager.get(type), name);
        return client.fetchByName(storeName).map(extensionStore -> converter.convertFrom(type, extensionStore));
    }

    // 按资源标识读取非结构化扩展。
    @Override
    public Mono<Unstructured> fetch(GroupVersionKind gvk, String name) {
        var storeName = ExtensionStoreUtil.buildStoreName(schemeManager.get(gvk), name);
        return client.fetchByName(storeName)
                .map(extensionStore -> converter.convertFrom(Unstructured.class, extensionStore));
    }

    // 以 JSON 扩展形式读取原始数据。
    private Mono<JsonExtension> fetchJsonExtension(GroupVersionKind gvk, String name) {
        var storeName = ExtensionStoreUtil.buildStoreName(schemeManager.get(gvk), name);
        return client.fetchByName(storeName)
                .map(extensionStore -> converter.convertFrom(JsonExtension.class, extensionStore));
    }

    // 读取扩展，缺失时抛出未找到异常。
    @Override
    public <E extends Extension> Mono<E> get(Class<E> type, String name) {
        return fetch(type, name).switchIfEmpty(Mono.error(() -> {
            var gvk = GroupVersionKind.fromExtension(type);
            return new ExtensionNotFoundException(gvk, name);
        }));
    }

    // 读取非结构化扩展，缺失时报错。
    private Mono<Unstructured> get(GroupVersionKind gvk, String name) {
        return fetch(gvk, name).switchIfEmpty(Mono.error(() -> new ExtensionNotFoundException(gvk, name)));
    }

    // 读取 JSON 扩展，缺失时报错。
    @Override
    public Mono<JsonExtension> getJsonExtension(GroupVersionKind gvk, String name) {
        return fetchJsonExtension(gvk, name).switchIfEmpty(Mono.error(() -> new ExtensionNotFoundException(gvk, name)));
    }

    // 创建扩展并保护客户端可写元数据。
    @Override
    public <E extends Extension> Mono<E> create(E extension) {
        return createExtension(extension, false);
    }

    // 内部创建，跳过用户元数据保护。
    @Override
    public <E extends Extension> Mono<E> createInternal(E extension) {
        return createExtension(extension, true);
    }

    // 规范化元数据，并在名称冲突时重试创建。
    private <E extends Extension> Mono<E> createExtension(E extension, boolean trusted) {
        return Mono.fromCallable(() -> {
                    var metadata = extension.getMetadata();
                    
                    metadata.setCreationTimestamp(Instant.now());
                    metadata.setDeletionTimestamp(null);
                    metadata.setVersion(null);

                    if (!hasText(metadata.getName())) {
                        if (!hasText(metadata.getGenerateName())) {
                            throw new IllegalArgumentException(
                                    "当 metadata.name 为" + "空白时，metadata.generateName 不能为空");
                        }

                        
                        metadata.setName(metadata.getGenerateName()
                                + secure().nextAlphanumeric(GENERATE_NAME_RANDOM_LENGTH)
                                        
                                        .toLowerCase());
                    }
                    extension.setMetadata(metadata);
                    if (!trusted) {
                        protectCreateMetadata(extension);
                    }
                    return converter.convertTo(extension);
                })
                .subscribeOn(this.scheduler)
                
                
                .flatMap(extStore -> doCreate(extension, extStore.getName(), extStore.getData()))
                .flatMap(created -> Mono.fromCallable(() -> {
                            watchers.onAdd(convertToRealExtension(created));
                            return created;
                        })
                        .subscribeOn(this.scheduler))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        
                        .filter(t -> (t instanceof DataIntegrityViolationException
                                        || t instanceof cn.tolim.pudans.infra.exception.DuplicateNameException)
                                && hasText(extension.getMetadata().getGenerateName())));
    }

    // 更新扩展并保护客户端可写元数据。
    @Override
    public <E extends Extension> Mono<E> update(E extension) {
        return updateExtension(extension, false);
    }

    // 内部更新，跳过用户元数据保护。
    @Override
    public <E extends Extension> Mono<E> updateInternal(E extension) {
        return updateExtension(extension, true);
    }

    // 合并旧元数据后更新，仅状态变化不通知观察者。
    private <E extends Extension> Mono<E> updateExtension(E extension, boolean trusted) {
        return getLatest(extension).flatMap(old -> {
            var oldJsonExt = new JsonExtension(objectMapper, old);
            var newJsonExt = new JsonExtension(objectMapper, extension);
            
            var oldMetadata = oldJsonExt.getMetadata();
            var newMetadata = newJsonExt.getMetadata();
            newMetadata.setCreationTimestamp(oldMetadata.getCreationTimestamp());
            newMetadata.setGenerateName(oldMetadata.getGenerateName());
            if (newMetadata.getVersion() == null) {
                newMetadata.setVersion(oldMetadata.getVersion());
            }

            
            
            if (!trusted) {
                protectUpdateMetadata(oldJsonExt, newJsonExt);
            }

            if (Objects.equals(oldJsonExt, newJsonExt)) {
                
                return Mono.just(extension);
            }

            var onlyStatusChanged = isOnlyStatusChanged(oldJsonExt.getInternal(), newJsonExt.getInternal());

            var store = this.converter.convertTo(newJsonExt);
            var doUpdate = doUpdate(extension, store.getName(), store.getVersion(), store.getData());
            if (!onlyStatusChanged) {
                doUpdate = doUpdate.flatMap(updated -> Mono.fromCallable(() -> {
                            watchers.onUpdate(convertToRealExtension(old), convertToRealExtension(updated));
                            return updated;
                        })
                        .subscribeOn(this.scheduler));
            }
            return doUpdate;
        });
    }

    // 拒绝客户端在创建时写入受保护的元数据。
    private static void protectCreateMetadata(Extension extension) {
        var metadata = extension.getMetadata();
        if (metadata != null
                && metadata.getFinalizers() != null
                && !metadata.getFinalizers().isEmpty()) {
            throw new AccessDeniedException("metadata.finalizers 是受保护的字段，不能通过 API 请求设置。");
        }
        if (!isUserKind(extension)) {
            return;
        }
        var reserved = ReservedLabels.of(metadata == null ? null : metadata.getLabels());
        if (!reserved.isEmpty()) {
            throw new AccessDeniedException(
                    "用户的保留标签为只读，不能通过 API 请求设置。");
        }
    }

    // 拒绝客户端在更新时篡改受保护的元数据。
    private static void protectUpdateMetadata(JsonExtension oldExtension, JsonExtension newExtension) {
        var oldMetadata = oldExtension.getMetadata();
        var newMetadata = newExtension.getMetadata();
        if (oldMetadata == null || newMetadata == null) {
            return;
        }

        var requestedDeletionTimestamp = newMetadata.getDeletionTimestamp();
        if (requestedDeletionTimestamp != null
                && !Objects.equals(requestedDeletionTimestamp, oldMetadata.getDeletionTimestamp())) {
            throw new AccessDeniedException("metadata.deletionTimestamp 是受保护的字段。");
        }
        newMetadata.setDeletionTimestamp(oldMetadata.getDeletionTimestamp());

        var requestedFinalizers = newMetadata.getFinalizers();
        // 删除中的对象同样不允许改写终结器，否则可清空终结器绕过清理
        if (requestedFinalizers != null
                && !requestedFinalizers.isEmpty()
                && !Objects.equals(
                        new HashSet<>(requestedFinalizers),
                        oldMetadata.getFinalizers() == null
                                ? Set.of()
                                : new HashSet<>(oldMetadata.getFinalizers()))) {
            throw new AccessDeniedException("metadata.finalizers 是受保护的字段。");
        }
        newMetadata.setFinalizers(oldMetadata.getFinalizers());

        if (!isUserKind(oldExtension)) {
            return;
        }
        var oldReservedLabels = ReservedLabels.of(oldMetadata.getLabels());
        var requestedReservedLabels = ReservedLabels.of(newMetadata.getLabels());
        if (!requestedReservedLabels.isEmpty() && !requestedReservedLabels.equals(oldReservedLabels)) {
            throw new AccessDeniedException("用户的保留标签为只读。");
        }
        if (!oldReservedLabels.isEmpty()) {
            var merged = new HashMap<String, String>(
                    newMetadata.getLabels() == null ? Map.of() : newMetadata.getLabels());
            merged.putAll(oldReservedLabels);
            newMetadata.setLabels(merged);
        }
    }

    // 仅用户类型需要保护保留标签。
    private static boolean isUserKind(Extension extension) {
        if (extension == null) {
            return false;
        }
        try {
            return User.KIND.equals(extension.getKind());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // 按扩展的具体表示读取最新版本。
    private Mono<? extends Extension> getLatest(Extension extension) {
        if (extension instanceof Unstructured) {
            return get(extension.groupVersionKind(), extension.getMetadata().getName());
        }
        if (extension instanceof JsonExtension) {
            return getJsonExtension(
                    extension.groupVersionKind(), extension.getMetadata().getName());
        }
        return get(extension.getClass(), extension.getMetadata().getName());
    }

    // 标记删除时间戳并持久化删除状态。
    @Override
    public <E extends Extension> Mono<E> delete(E extension) {
        
        Assert.notNull(extension.getMetadata().getVersion(), "扩展版本不能为 null");
        
        extension.getMetadata().setDeletionTimestamp(Instant.now());
        var es = converter.convertTo(extension);
        return doUpdate(extension, es.getName(), es.getVersion(), es.getData())
                .flatMap(deleted -> Mono.fromCallable(() -> {
                            watchers.onDelete(convertToRealExtension(extension));
                            return deleted;
                        })
                        .subscribeOn(this.scheduler));
    }

    // 暴露基于索引的只读查询入口。
    @Override
    public IndexedQueryEngine indexedQueryEngine() {
        return new IndexedQueryEngine() {
            // 按标识分页查询对象键。
            @Override
            public ListResult<String> retrieve(GroupVersionKind gvk, ListOptions options, PageRequest page) {
                var scheme = schemeManager.get(gvk);
                return indexEngine.retrieve(scheme.type(), options, page);
            }

            // 返回符合条件的全部对象键。
            @Override
            public List<String> retrieveAll(GroupVersionKind gvk, ListOptions options, Sort sort) {
                var scheme = schemeManager.get(gvk);
                return StreamSupport.stream(
                                indexEngine
                                        .retrieveAll(scheme.type(), options, sort)
                                        .spliterator(),
                                false)
                        .toList();
            }
        };
    }

    

    // 在同一事务内写入存储并更新索引。
    @SuppressWarnings("unchecked")
    <E extends Extension> Mono<E> doCreate(E oldExtension, String name, byte[] data) {
        return Mono.defer(() -> {
            var type = (Class<E>) oldExtension.getClass();
            return client.create(name, data)
                    .map(created -> converter.convertFrom(type, created))
                    .flatMap(extension -> Mono.fromCallable(() -> {
                                this.indexEngine.insert(List.of(convertToRealExtension(extension)));
                                return extension;
                            })
                            .subscribeOn(this.scheduler))
                    .as(transactionalOperator::transactional);
        });
    }

    

    // 在同一事务内更新存储与索引数据。
    @SuppressWarnings("unchecked")
    <E extends Extension> Mono<E> doUpdate(E oldExtension, String name, Long version, byte[] data) {
        return Mono.defer(() -> {
            var type = (Class<E>) oldExtension.getClass();
            return client.update(name, version, data)
                    .map(updated -> converter.convertFrom(type, updated))
                    .flatMap(extension -> Mono.fromCallable(() -> {
                                this.indexEngine.update(List.of(convertToRealExtension(extension)));
                                return extension;
                            })
                            .subscribeOn(this.scheduler))
                    .as(transactionalOperator::transactional);
        });
    }

    // 将通用表示转换为方案注册的真实类型。
    private Extension convertToRealExtension(Extension extension) {
        var gvk = extension.groupVersionKind();
        var realType = schemeManager.get(gvk).type();
        Extension realExtension = extension;
        if (extension instanceof Unstructured) {
            realExtension = Unstructured.OBJECT_MAPPER.convertValue(extension, realType);
        } else if (extension instanceof JsonExtension jsonExtension) {
            realExtension = jsonExtension.getObjectMapper().convertValue(jsonExtension, realType);
        }
        return realExtension;
    }

    // 注册观察者以接收后续变更通知。
    @Override
    public void watch(Watcher watcher) {
        this.watchers.addWatcher(watcher);
    }

    // 仅状态变化时无需通知观察者。
    private static boolean isOnlyStatusChanged(ObjectNode oldNode, ObjectNode newNode) {
        if (Objects.equals(oldNode, newNode)) {
            return false;
        }
        
        
        var oldFields = new HashSet<String>();
        var newFields = new HashSet<String>();
        oldNode.fieldNames().forEachRemaining(oldFields::add);
        newNode.fieldNames().forEachRemaining(newFields::add);
        oldFields.remove("status");
        newFields.remove("status");
        if (!Objects.equals(oldFields, newFields)) {
            return false;
        }
        for (var field : oldFields) {
            if (!Objects.equals(oldNode.get(field), newNode.get(field))) {
                return false;
            }
        }
        return true;
    }
}
