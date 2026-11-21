package cn.tolim.pudans.core.storage;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;
import static cn.tolim.pudans.extension.contract.index.query.Queries.lessThan;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.PageRequestImpl;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FileRecords {

    private final ReactiveExtensionClient client;

    // 创建记录前补齐名称，保证扩展标识稳定。
    public Mono<StorageFile> create(StorageFile file) {
        return Mono.fromCallable(() -> {
                    if (file.getMetadata() == null) {
                        file.setMetadata(new Metadata());
                    }
                    if (!StringUtils.hasText(file.getMetadata().getName())) {
                        file.getMetadata().setName(UUID.randomUUID().toString());
                    }
                    return file;
                })
                .flatMap(client::create);
    }

    // 覆盖保存文件记录并返回最新版本。
    public Mono<StorageFile> update(StorageFile file) {
        return client.update(file);
    }

    // 按标识读取记录，缺失时返回空结果。
    public Mono<StorageFile> get(String fileId) {
        return client.get(StorageFile.class, fileId);
    }

    // 从扩展客户端获取最新文件记录。
    public Mono<StorageFile> fetch(String fileId) {
        return client.fetch(StorageFile.class, fileId);
    }

    // 按查询条件分页返回文件记录。
    public Mono<ListResult<StorageFile>> list(StorageFileQuery query) {
        return client.listBy(
                StorageFile.class,
                toListOptions(query),
                PageRequestImpl.of(query.page(), query.size(), defaultSort()));
    }

    // 统计满足查询条件的记录数量。
    public Mono<Long> count(StorageFileQuery query) {
        return client.countBy(StorageFile.class, toListOptions(query));
    }

    // 判断策略下是否仍有文件引用，供删除保护使用。
    public Mono<Boolean> existsByPolicy(String policyName) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.policy", policyName))
                .build();
        return client.countBy(StorageFile.class, options).map(count -> count > 0);
    }

    // 列出超过上传期限且仍在上传的记录。
    public Flux<StorageFile> listExpiredUploads(Instant now, int limit) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.state", StorageFileState.UPLOADING))
                .andQuery(lessThan("spec.uploadExpiresAt", now, true))
                .build();
        return client.listBy(StorageFile.class, options, PageRequestImpl.of(1, limit, defaultSort()))
                .flatMapMany(result -> Flux.fromIterable(result.getItems()));
    }

    // 列出仍在上传状态的记录名称，供本地会话清理使用。
    public Flux<String> listUploadingNames() {
        var options = ListOptions.builder()
                .andQuery(equal("spec.state", StorageFileState.UPLOADING))
                .build();
        return client.listAllNames(StorageFile.class, options, defaultSort());
    }

    // 列出已到清理时间的回收站记录。
    public Flux<StorageFile> listExpiredTrash(Instant now, int limit) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.state", StorageFileState.TRASHED))
                .andQuery(lessThan("spec.trashExpiresAt", now, true))
                .build();
        return client.listBy(StorageFile.class, options, PageRequestImpl.of(1, limit, defaultSort()))
                .flatMapMany(result -> Flux.fromIterable(result.getItems()));
    }

    // 删除记录并等待删除操作完成。
    public Mono<Void> delete(StorageFile file) {
        return client.delete(file).then();
    }

    // 将查询条件转换为扩展索引过滤条件。
    private static ListOptions toListOptions(StorageFileQuery query) {
        var builder = ListOptions.builder();
        if (query.state() != null) {
            builder.andQuery(equal("spec.state", query.state()));
        }
        if (StringUtils.hasText(query.policy())) {
            builder.andQuery(equal("spec.policy", query.policy()));
        }
        if (StringUtils.hasText(query.owner())) {
            builder.andQuery(equal("spec.owner", query.owner()));
        }
        if (StringUtils.hasText(query.module())) {
            builder.andQuery(equal("spec.module", query.module()));
        }
        if (StringUtils.hasText(query.extension())) {
            builder.andQuery(equal("spec.extension", query.extension()));
        }
        return builder.build();
    }
}
