package cn.tolim.pudans.core.creation.archive;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.AssetRegisterRequest;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.core.creation.task.GenerationResultAssets;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ArchivedResultAssets implements GenerationResultAssets {

    private static final String SOURCE_CREATION = "creation";

    private final Assets assets;

    @Override
    public Mono<List<String>> register(
            RequestIdentity identity, GenerationTask task, List<StorageFile> storageFiles) {
        return Flux.fromIterable(storageFiles)
                .concatMap(file -> assets.register(request(task, file), identity))
                .map(asset -> asset.id().toString())
                .collectList();
    }

    private static AssetRegisterRequest request(GenerationTask task, StorageFile file) {
        var spec = file.getSpec();
        var metadata = new LinkedHashMap<String, Object>();
        if (spec.getAttributes() != null) {
            metadata.putAll(spec.getAttributes());
        }
        metadata.put("modelId", task.modelId().toString());
        return new AssetRegisterRequest(
                file.getMetadata().getName(),
                task.mediaType().getCode(),
                SOURCE_CREATION,
                task.id(),
                task.parentAssetId(),
                spec.getOriginalName(),
                null,
                task.prompt(),
                task.settings(),
                spec.getContentType(),
                spec.getSize(),
                null,
                null,
                null,
                List.of(),
                Map.copyOf(metadata));
    }
}
