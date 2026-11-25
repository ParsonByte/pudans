package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.SandboxSnapshot;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.core.storage.StorageRoutes;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.StorageIo;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.r2dbc.agent.sandbox.SandboxStore;
import cn.tolim.pudans.infra.utils.Digests;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class SandboxSnapshots {

    private static final String MODULE = "agent-sandbox";

    private static final String PURPOSEFUL_ROUTE =
            StorageRoute.Purpose.SANDBOX_SNAPSHOT.getCode();

    private final StorageRoutes routes;

    private final StorageTransfer storageTransfer;

    private final SandboxStore store;

    private final SandboxProperties properties;

    private final PudansProperties pudansProperties;

    private Clock clock = Clock.systemUTC();

    // 快照必须使用显式配置的沙箱快照路由，不回退普通上传默认策略。
    public Mono<StoragePolicy> route() {
        return routes.resolve(StorageRoute.Purpose.SANDBOX_SNAPSHOT);
    }

    // 把工作区归档流写入对象存储，再落快照元数据。
    public Mono<SandboxSnapshot> store(
            Sandbox sandbox, StoragePolicy policy, InputStream archive) {
        var snapshotId = UUID.randomUUID().toString();
        var file = snapshotId + ".tar";
        var originPath = "sandboxes/" + sandbox.id() + "/snapshots/" + file;
        var tempDirectory = pudansProperties
                .getWorkDir()
                .toAbsolutePath()
                .normalize()
                .resolve("sandbox/.tmp");
        return StorageIo.spool(
                        StorageIo.read(archive, -1),
                        tempDirectory,
                        maxBytes(policy))
                .flatMap(path -> Mono.usingWhen(
                        Mono.just(path),
                        ignored -> Mono.fromCallable(() -> Files.size(path))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(size -> {
                                    var checksum = Digests.sha256Hex(path);
                                    var attributes = new LinkedHashMap<String, String>();
                                    attributes.put("sandboxId", sandbox.id().toString());
                                    attributes.put("snapshotId", snapshotId);
                                    attributes.put("checksum", checksum);
                                    var request = new StorageWriteRequest(
                                            policy.getMetadata().getName(),
                                            sandbox.ownerId(),
                                            MODULE,
                                            originPath,
                                            file,
                                            "application/x-tar",
                                            size,
                                            Map.copyOf(attributes),
                                            Flux.defer(() -> read(path, size)),
                                            false);
                                    return storageTransfer
                                            .upload(request)
                                            .flatMap(uploaded -> insert(
                                                            sandbox,
                                                            policy,
                                                            snapshotId,
                                                            checksum,
                                                            uploaded)
                                                    .onErrorResume(error -> cleanupUploaded(uploaded)
                                                            .then(Mono.error(error))));
                                }),
                        ignored -> deleteTemp(path),
                        (ignored, error) -> deleteTemp(path),
                        ignored -> deleteTemp(path)))
                .doFinally(signal -> closeQuietly(archive));
    }

    private Mono<SandboxSnapshot> insert(
            Sandbox sandbox,
            StoragePolicy policy,
            String snapshotId,
            String checksum,
            StorageFile uploaded) {
        var snapshot = new SandboxSnapshot(
                UUID.randomUUID(),
                sandbox.id(),
                sandbox.provider(),
                snapshotId,
                policy.getSpec().getType().getCode(),
                PURPOSEFUL_ROUTE,
                policy.getMetadata().getName(),
                uploaded.getSpec().getStorageKey(),
                uploaded.getSpec().getSize(),
                checksum,
                SandboxSnapshot.Status.READY,
                now(),
                now().plus(properties.normalizedSnapshotTtl()));
        return store.insertSnapshot(snapshot);
    }

    private Mono<Void> cleanupUploaded(StorageFile uploaded) {
        var fileId = uploaded.getMetadata() == null ? null : uploaded.getMetadata().getName();
        if (fileId == null) {
            return Mono.empty();
        }
        return storageTransfer
                .trash(fileId)
                .then(storageTransfer.purge(fileId))
                .onErrorResume(ignored -> Mono.empty());
    }

    private static Mono<Void> deleteTemp(java.nio.file.Path path) {
        return Mono.fromRunnable(() -> StorageIo.deleteQuietly(path))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private static Flux<org.springframework.core.io.buffer.DataBuffer> read(
            java.nio.file.Path path, long size) {
        try {
            return StorageIo.read(Files.newInputStream(path), size);
        } catch (IOException error) {
            return Flux.error(error);
        }
    }

    private static long maxBytes(StoragePolicy policy) {
        return policy.getSpec() == null ? 0 : policy.getSpec().getMaxSize();
    }

    private static void closeQuietly(InputStream archive) {
        try {
            archive.close();
        } catch (Exception ignored) {
        }
    }

    private Instant now() {
        return clock.instant();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
