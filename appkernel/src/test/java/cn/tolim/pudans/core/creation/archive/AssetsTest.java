package cn.tolim.pudans.core.creation.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.AssetRegisterRequest;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.r2dbc.creation.GenerationTaskStore;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AssetsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    AssetRepository assets;

    @Mock
    GenerationTaskStore generationTasks;

    @Mock
    StorageTransfer storageTransfer;

    private Assets service;

    @BeforeEach
    void setUp() {
        service = new Assets(assets, generationTasks, storageTransfer);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void passesOwnerFiltersAndCursorPaginationToTheRepository() {
        var items = IntStream.range(0, 2)
                .mapToObj(index -> asset(UUID.randomUUID(), "alice", "workspace-a", null, null, NOW))
                .toList();
        when(assets.list(eq(ALICE), eq("image"), eq("creation"), eq("cat"), isNull(), eq(21)))
                .thenReturn(Mono.just(items));

        StepVerifier.create(service.items(ALICE, "image", "creation", "cat", null, 20))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(2);
                    assertThat(page.nextCursor()).isNull();
                    assertThat(page.limit()).isEqualTo(20);
                    assertThat(page.emptyState()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void returnsACursorOnlyWhenAnotherPageExists() {
        var items = IntStream.range(0, 21)
                .mapToObj(index -> asset(
                        UUID.randomUUID(), "alice", "workspace-a", null, null, NOW.plusSeconds(index)))
                .toList();
        when(assets.list(eq(ALICE), isNull(), isNull(), isNull(), isNull(), eq(21)))
                .thenReturn(Mono.just(items));

        StepVerifier.create(service.items(ALICE, null, null, null, null, null))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(20);
                    assertThat(page.nextCursor()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void keepsAnotherOwnersAssetHidden() {
        var assetId = UUID.randomUUID();
        when(assets.findOwned(assetId, ALICE)).thenReturn(Mono.empty());

        StepVerifier.create(service.view(ALICE, assetId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(404);
                            assertThat(api.getCode()).isEqualTo("asset-not-found");
                        }))
                .verify();
    }

    @Test
    void buildsTheParentAssetChain() {
        var root = asset(UUID.randomUUID(), "alice", "workspace-a", null, "file-root", NOW);
        var child = asset(UUID.randomUUID(), "alice", "workspace-a", root.id(), "file-child", NOW);
        when(assets.findOwned(child.id(), ALICE)).thenReturn(Mono.just(child));
        when(assets.findOwned(root.id(), ALICE)).thenReturn(Mono.just(root));

        StepVerifier.create(service.view(ALICE, child.id()))
                .assertNext(view -> {
                    assertThat(view.asset().id()).isEqualTo(child.id());
                    assertThat(view.ancestors()).extracting(Asset::id).containsExactly(root.id());
                })
                .verifyComplete();
    }

    @Test
    void reuseReturnsTheSameStorageFileWithoutUploadingOrInserting() {
        var assetId = UUID.randomUUID();
        var asset = asset(assetId, "alice", "workspace-a", null, "file-1", NOW);
        when(assets.findOwned(assetId, ALICE)).thenReturn(Mono.just(asset));

        StepVerifier.create(service.reuse(ALICE, assetId))
                .assertNext(draft -> {
                    assertThat(draft.sourceAssetId()).isEqualTo(assetId);
                    assertThat(draft.parentAssetId()).isEqualTo(assetId);
                    assertThat(draft.storageFileId()).isEqualTo("file-1");
                    assertThat(draft.prompt()).isEqualTo("prompt");
                    assertThat(draft.settings()).containsEntry("ratio", "1:1");
                })
                .verifyComplete();

        verify(assets, never()).insert(any());
        verifyNoInteractions(storageTransfer);
    }

    @Test
    void registersAnExistingReadyFileAndDerivesItsMetadata() {
        var file = storageFile("alice", StorageFileState.ACTIVE);
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(assets.findByStorageFile("file-1", null, ALICE)).thenReturn(Mono.empty());
        when(assets.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var request = new AssetRegisterRequest(
                "file-1", "image", "creation", null, null, null, "desc", "prompt",
                Map.of("ratio", "1:1"), null, null, 1024, 768, null, List.of("cat"), Map.of("k", "v"));

        StepVerifier.create(service.register(request, ALICE))
                .assertNext(asset -> {
                    assertThat(asset.ownerId()).isEqualTo("alice");
                    assertThat(asset.workspaceId()).isEqualTo("workspace-a");
                    assertThat(asset.storageFileId()).isEqualTo("file-1");
                    assertThat(asset.contentType()).isEqualTo("image/png");
                    assertThat(asset.size()).isEqualTo(42L);
                    assertThat(asset.width()).isEqualTo(1024);
                    assertThat(asset.tags()).containsExactly("cat");
                    assertThat(asset.status()).isEqualTo(CreationValues.ASSET_STATUS_ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void doesNotRegisterAFileOwnedByAnotherUser() {
        when(storageTransfer.getFile("file-1"))
                .thenReturn(Mono.just(storageFile("bob", StorageFileState.ACTIVE)));
        var request = new AssetRegisterRequest(
                "file-1", "image", "upload", null, null, null, null, null,
                Map.of(), null, null, null, null, null, List.of(), Map.of());

        StepVerifier.create(service.register(request, ALICE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api ->
                                assertThat(api.getCode()).isEqualTo("asset-storage-file-not-found")))
                .verify();

        verify(assets, never()).insert(any());
    }

    @Test
    void returnsTheExistingAssetWhenTheSameGenerationFileIsRegisteredAgain() {
        var existing = asset(UUID.randomUUID(), "alice", "workspace-a", null, "file-1", NOW);
        var taskId = UUID.randomUUID();
        when(generationTasks.findById(taskId))
                .thenReturn(Mono.just(generationTask(taskId, "alice", "workspace-a")));
        when(storageTransfer.getFile("file-1"))
                .thenReturn(Mono.just(storageFile("alice", StorageFileState.ACTIVE)));
        when(assets.findByStorageFile("file-1", taskId, ALICE)).thenReturn(Mono.just(existing));
        var request = new AssetRegisterRequest(
                "file-1", "image", "creation", taskId, null, null, null, "prompt",
                Map.of(), "image/png", 42L, null, null, null, List.of(), Map.of());

        StepVerifier.create(service.register(request, ALICE))
                .assertNext(asset -> assertThat(asset.id()).isEqualTo(existing.id()))
                .verifyComplete();

        verify(assets, never()).insert(any());
    }

    @Test
    void doesNotRegisterAFileAgainstAnotherOwnersGenerationTask() {
        var taskId = UUID.randomUUID();
        when(generationTasks.findById(taskId))
                .thenReturn(Mono.just(generationTask(taskId, "bob", "workspace-b")));
        var request = new AssetRegisterRequest(
                "file-1", "image", "creation", taskId, null, null, null, "prompt",
                Map.of(), "image/png", 42L, null, null, null, List.of(), Map.of());

        StepVerifier.create(service.register(request, ALICE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(404);
                            assertThat(api.getCode()).isEqualTo("asset-generation-task-not-found");
                        }))
                .verify();

        verifyNoInteractions(storageTransfer);
        verify(assets, never()).insert(any());
    }

    @Test
    void facetsHaveAnEmptyStateWhenThereAreNoAssets() {
        when(assets.kindFacets(ALICE)).thenReturn(Mono.just(List.of()));
        when(assets.sourceFacets(ALICE)).thenReturn(Mono.just(List.of()));
        when(assets.count(ALICE)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.facets(ALICE))
                .assertNext(facets -> {
                    assertThat(facets.total()).isZero();
                    assertThat(facets.kinds()).isEmpty();
                    assertThat(facets.sources()).isEmpty();
                    assertThat(facets.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void storesTheRegisteredAssetUnderTheResolvedIdentity() {
        var file = storageFile("alice", StorageFileState.ACTIVE);
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(assets.findByStorageFile("file-1", null, ALICE)).thenReturn(Mono.empty());
        when(assets.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var request = new AssetRegisterRequest(
                "file-1", "image", "upload", null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        service.register(request, ALICE).block();

        var captor = ArgumentCaptor.forClass(Asset.class);
        verify(assets).insert(captor.capture());
        assertThat(captor.getValue().settings()).isEmpty();
        assertThat(captor.getValue().tags()).isEmpty();
        assertThat(captor.getValue().metadata()).isEmpty();
    }

    @Test
    void rejectsAnUnsupportedAssetKindBeforeReadingTheStorageFile() {
        var request = new AssetRegisterRequest(
                "file-1", "archive", "upload", null, null, null, null, null,
                Map.of(), null, null, null, null, null, List.of(), Map.of());

        StepVerifier.create(service.register(request, ALICE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(400);
                            assertThat(api.getCode()).isEqualTo("asset-type-invalid");
                        }))
                .verify();

        verifyNoInteractions(storageTransfer);
        verify(assets, never()).insert(any());
    }

    private static StorageFile storageFile(String owner, StorageFileState state) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        var spec = new StorageFile.Spec();
        spec.setOwner(owner);
        spec.setState(state);
        spec.setOriginalName("sample.png");
        spec.setContentType("image/png");
        spec.setSize(42L);
        file.setSpec(spec);
        return file;
    }

    private static Asset asset(
            UUID id,
            String owner,
            String workspace,
            UUID parent,
            String storageFileId,
            Instant updatedAt) {
        return new Asset(
                id,
                owner,
                workspace,
                "image",
                "creation",
                storageFileId,
                null,
                parent,
                "sample",
                null,
                "prompt",
                Map.of("ratio", "1:1"),
                "image/png",
                42L,
                1024,
                768,
                null,
                List.of("cat"),
                Map.of(),
                CreationValues.ASSET_STATUS_ACTIVE,
                updatedAt,
                updatedAt);
    }

    private static GenerationTask generationTask(
            UUID taskId, String owner, String workspace) {
        return new GenerationTask(
                taskId,
                owner,
                workspace,
                null,
                null,
                null,
                null,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                UUID.randomUUID(),
                "prompt",
                Map.of(),
                List.of(),
                GenerationTask.Status.ARCHIVING,
                true,
                "provider-task-1",
                List.of(),
                null,
                null,
                null,
                "generation-task-" + taskId,
                NOW,
                NOW,
                null);
    }
}
