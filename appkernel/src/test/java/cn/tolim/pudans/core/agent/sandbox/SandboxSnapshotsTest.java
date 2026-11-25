package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.core.storage.StorageRoutes;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.r2dbc.agent.sandbox.SandboxStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SandboxSnapshotsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final byte[] ARCHIVE = "workspace-archive".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path workDir;

    @Mock
    StorageRoutes routes;

    @Mock
    StorageTransfer transfer;

    @Mock
    SandboxStore store;

    private SandboxSnapshots snapshots;

    @BeforeEach
    void setUp() {
        var properties = new SandboxProperties();
        properties.setSnapshotTtl(Duration.ofDays(2));
        var pudans = new PudansProperties();
        pudans.setWorkDir(workDir);
        snapshots = new SandboxSnapshots(routes, transfer, store, properties, pudans);
        snapshots.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void resolvesOnlyTheExplicitSandboxSnapshotRoute() {
        var policy = policy();
        when(routes.resolve(StorageRoute.Purpose.SANDBOX_SNAPSHOT)).thenReturn(Mono.just(policy));

        StepVerifier.create(snapshots.route())
                .expectNext(policy)
                .verifyComplete();
    }

    @Test
    void storesTheArchiveAndSnapshotMetadataWithAChecksum() throws Exception {
        var sandbox = sandbox();
        var policy = policy();
        var file = storageFile();
        var checksum = sha256(ARCHIVE);
        when(transfer.upload(any())).thenReturn(Mono.just(file));
        when(store.insertSnapshot(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(snapshots.store(
                        sandbox,
                        policy,
                        new ByteArrayInputStream(ARCHIVE)))
                .assertNext(snapshot -> {
                    assertThat(snapshot.sandboxId()).isEqualTo(sandbox.id());
                    assertThat(snapshot.provider()).isEqualTo("agentrun");
                    assertThat(snapshot.storageKind()).isEqualTo("s3");
                    assertThat(snapshot.storageRoutePurpose()).isEqualTo("sandbox_snapshot");
                    assertThat(snapshot.storagePolicyId()).isEqualTo("snapshot-policy");
                    assertThat(snapshot.objectKey()).isEqualTo("sandbox/snapshot.tar");
                    assertThat(snapshot.sizeBytes()).isEqualTo((long) ARCHIVE.length);
                    assertThat(snapshot.checksum()).isEqualTo(checksum);
                    assertThat(snapshot.status()).isEqualTo(cn.tolim.pudans.contract.agent.SandboxSnapshot.Status.READY);
                    assertThat(snapshot.createdAt()).isEqualTo(NOW);
                    assertThat(snapshot.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(2)));
                })
                .verifyComplete();

        var request = ArgumentCaptor.forClass(StorageWriteRequest.class);
        verify(transfer).upload(request.capture());
        assertThat(request.getValue().policy()).isEqualTo("snapshot-policy");
        assertThat(request.getValue().owner()).isEqualTo("alice");
        assertThat(request.getValue().module()).isEqualTo("agent-sandbox");
        assertThat(request.getValue().contentType()).isEqualTo("application/x-tar");
        assertThat(request.getValue().size()).isEqualTo(ARCHIVE.length);
        assertThat(request.getValue().attributes())
                .containsEntry("sandboxId", sandbox.id().toString())
                .containsEntry("checksum", checksum);

        try (var files = Files.list(workDir.resolve("sandbox/.tmp"))) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void removesTheUploadedObjectWhenSnapshotMetadataCannotBePersisted() {
        var sandbox = sandbox();
        var file = storageFile();
        when(transfer.upload(any())).thenReturn(Mono.just(file));
        when(transfer.trash("file-1")).thenReturn(Mono.just(file));
        when(transfer.purge("file-1")).thenReturn(Mono.empty());
        when(store.insertSnapshot(any())).thenReturn(Mono.error(new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "sandbox-snapshot-persist-failed",
                "快照元数据写入失败。")));

        StepVerifier.create(snapshots.store(
                        sandbox,
                        policy(),
                        new ByteArrayInputStream(ARCHIVE)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getCode()).isEqualTo("sandbox-snapshot-persist-failed");
                        }))
                .verify();

        verify(transfer).trash("file-1");
        verify(transfer).purge("file-1");
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName("snapshot-policy");
        policy.setMetadata(metadata);
        policy.getSpec().setType(StoragePolicyType.S3);
        policy.getSpec().setMaxSize(1024 * 1024);
        return policy;
    }

    private static StorageFile storageFile() {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        file.getSpec().setStorageKey("sandbox/snapshot.tar");
        file.getSpec().setSize(ARCHIVE.length);
        return file;
    }

    private static Sandbox sandbox() {
        return new Sandbox(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                UUID.randomUUID(),
                null,
                "agentrun",
                "default-template",
                "cn-shanghai",
                Sandbox.Status.READY,
                "/home/pudans/workspace",
                NOW.plusSeconds(1_800),
                NOW,
                NOW,
                NOW,
                null,
                null,
                null);
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }
}
