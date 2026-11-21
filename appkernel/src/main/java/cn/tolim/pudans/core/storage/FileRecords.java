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

    public Mono<StorageFile> update(StorageFile file) {
        return client.update(file);
    }

    public Mono<StorageFile> get(String fileId) {
        return client.get(StorageFile.class, fileId);
    }

    public Mono<StorageFile> fetch(String fileId) {
        return client.fetch(StorageFile.class, fileId);
    }

    public Mono<ListResult<StorageFile>> list(StorageFileQuery query) {
        return client.listBy(
                StorageFile.class,
                toListOptions(query),
                PageRequestImpl.of(query.page(), query.size(), defaultSort()));
    }

    public Mono<Long> count(StorageFileQuery query) {
        return client.countBy(StorageFile.class, toListOptions(query));
    }

    public Mono<Boolean> existsByPolicy(String policyName) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.policy", policyName))
                .build();
        return client.countBy(StorageFile.class, options).map(count -> count > 0);
    }

    public Flux<StorageFile> listExpiredUploads(Instant now, int limit) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.state", StorageFileState.UPLOADING))
                .andQuery(lessThan("spec.uploadExpiresAt", now, true))
                .build();
        return client.listBy(StorageFile.class, options, PageRequestImpl.of(1, limit, defaultSort()))
                .flatMapMany(result -> Flux.fromIterable(result.getItems()));
    }

    public Flux<StorageFile> listExpiredTrash(Instant now, int limit) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.state", StorageFileState.TRASHED))
                .andQuery(lessThan("spec.trashExpiresAt", now, true))
                .build();
        return client.listBy(StorageFile.class, options, PageRequestImpl.of(1, limit, defaultSort()))
                .flatMapMany(result -> Flux.fromIterable(result.getItems()));
    }

    public Mono<Void> delete(StorageFile file) {
        return client.delete(file).then();
    }

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
