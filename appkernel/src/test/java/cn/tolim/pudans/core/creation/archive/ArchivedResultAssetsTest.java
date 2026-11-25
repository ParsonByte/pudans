package cn.tolim.pudans.core.creation.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.AssetRegisterRequest;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ArchivedResultAssetsTest {

    private static final RequestIdentity IDENTITY = new RequestIdentity("alice", "workspace-a");

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    @Mock
    Assets assets;

    private ArchivedResultAssets registrar;

    @BeforeEach
    void setUp() {
        registrar = new ArchivedResultAssets(assets);
    }

    @Test
    void registersEachArchivedFileAsATaskResultAsset() {
        var taskId = UUID.randomUUID();
        var modelId = UUID.randomUUID();
        var parentAssetId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        var task = task(taskId, modelId, parentAssetId, runId);
        var image = storageFile(
                "file-1",
                "result.png",
                "image/png",
                42L,
                Map.of(
                        "generationTaskId", taskId.toString(),
                        "mediaType", "image",
                        "providerAssetId", "provider-asset-1",
                        "providerTaskId", "provider-task-1",
                        "runId", runId.toString()));
        var secondImage = storageFile(
                "file-2",
                "result.webp",
                "image/webp",
                2048L,
                Map.of(
                        "generationTaskId", taskId.toString(),
                        "mediaType", "image",
                        "providerAssetId", "provider-asset-2",
                        "providerTaskId", "provider-task-1",
                        "runId", runId.toString()));
        var firstAsset = asset(UUID.randomUUID());
        var secondAsset = asset(UUID.randomUUID());
        when(assets.register(any(AssetRegisterRequest.class), eq(IDENTITY)))
                .thenReturn(Mono.just(firstAsset))
                .thenReturn(Mono.just(secondAsset));

        StepVerifier.create(registrar.register(IDENTITY, task, List.of(image, secondImage)))
                .assertNext(ids -> assertThat(ids)
                        .containsExactly(firstAsset.id().toString(), secondAsset.id().toString()))
                .verifyComplete();

        var requests = ArgumentCaptor.forClass(AssetRegisterRequest.class);
        verify(assets, times(2)).register(requests.capture(), eq(IDENTITY));
        var registrations = requests.getAllValues();
        assertThat(registrations)
                .allSatisfy(request -> {
                    assertThat(request.source()).isEqualTo("creation");
                    assertThat(request.generationTaskId()).isEqualTo(taskId);
                    assertThat(request.parentAssetId()).isEqualTo(parentAssetId);
                    assertThat(request.prompt()).isEqualTo("prompt");
                    assertThat(request.settings()).containsEntry("ratio", "1:1");
                    assertThat(request.metadata())
                            .containsEntry("modelId", modelId.toString())
                            .containsEntry("providerTaskId", "provider-task-1")
                            .containsEntry("runId", runId.toString());
                });
        assertThat(registrations.get(0))
                .satisfies(request -> {
                    assertThat(request.storageFileId()).isEqualTo("file-1");
                    assertThat(request.kind()).isEqualTo("image");
                    assertThat(request.name()).isEqualTo("result.png");
                    assertThat(request.contentType()).isEqualTo("image/png");
                    assertThat(request.size()).isEqualTo(42L);
                    assertThat(request.metadata()).containsEntry("providerAssetId", "provider-asset-1");
                });
        assertThat(registrations.get(1))
                .satisfies(request -> {
                    assertThat(request.storageFileId()).isEqualTo("file-2");
                    assertThat(request.name()).isEqualTo("result.webp");
                    assertThat(request.contentType()).isEqualTo("image/webp");
                    assertThat(request.size()).isEqualTo(2048L);
                    assertThat(request.metadata()).containsEntry("providerAssetId", "provider-asset-2");
                });
    }

    private static GenerationTask task(
            UUID taskId, UUID modelId, UUID parentAssetId, UUID runId) {
        return new GenerationTask(
                taskId,
                IDENTITY.ownerId(),
                IDENTITY.workspaceId(),
                null,
                runId,
                null,
                parentAssetId,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                modelId,
                "prompt",
                Map.of("ratio", "1:1"),
                List.of(),
                GenerationTask.Status.ARCHIVING,
                true,
                "provider-task-1",
                List.of(),
                null,
                null,
                null,
                "generation-result-assets-test",
                NOW,
                NOW,
                null);
    }

    private static StorageFile storageFile(
            String fileId,
            String originalName,
            String contentType,
            long size,
            Map<String, String> attributes) {
        var metadata = new Metadata();
        metadata.setName(fileId);
        var spec = new StorageFile.Spec();
        spec.setOwner(IDENTITY.ownerId());
        spec.setState(StorageFileState.ACTIVE);
        spec.setOriginalName(originalName);
        spec.setContentType(contentType);
        spec.setSize(size);
        spec.setAttributes(attributes);
        var file = new StorageFile();
        file.setMetadata(metadata);
        file.setSpec(spec);
        return file;
    }

    private static Asset asset(UUID id) {
        return new Asset(
                id,
                IDENTITY.ownerId(),
                IDENTITY.workspaceId(),
                "image",
                "creation",
                "file",
                null,
                null,
                "result",
                null,
                "prompt",
                Map.of("ratio", "1:1"),
                "image/png",
                42L,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                CreationValues.ASSET_STATUS_ACTIVE,
                NOW,
                NOW);
    }
}
