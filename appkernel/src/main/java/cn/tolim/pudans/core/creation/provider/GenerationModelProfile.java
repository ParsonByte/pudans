package cn.tolim.pudans.core.creation.provider;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.util.Set;
import java.util.UUID;

public record GenerationModelProfile(
        UUID modelId,
        String provider,
        String providerModel,
        UUID channelId,
        Set<GenerationTask.MediaType> mediaTypes,
        Set<GenerationTask.Operation> operations) {

    public GenerationModelProfile {
        if (modelId == null) {
            throw new IllegalArgumentException("modelId must not be null");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (providerModel == null || providerModel.isBlank()) {
            throw new IllegalArgumentException("providerModel must not be blank");
        }
        mediaTypes = mediaTypes == null ? Set.of() : Set.copyOf(mediaTypes);
        operations = operations == null ? Set.of() : Set.copyOf(operations);
    }
}
