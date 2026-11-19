package cn.tolim.pudans.core.ai.provider;

import java.util.List;

public record AiProviderFilePage(
        String object,
        List<AiProviderFile> data,
        String firstId,
        String lastId,
        boolean hasMore) {

    public AiProviderFilePage {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
