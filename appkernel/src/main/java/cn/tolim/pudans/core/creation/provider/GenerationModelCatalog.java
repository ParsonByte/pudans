package cn.tolim.pudans.core.creation.provider;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface GenerationModelCatalog {

    // 由 P1 模型配置提供真实模型、渠道和供应商映射。
    Mono<GenerationModelProfile> resolve(UUID modelId);

    // 参数和提示词校验由模型 schema 负责，P2 不复制规则。
    Mono<Void> validate(
            GenerationModelProfile profile,
            GenerationTask.MediaType mediaType,
            GenerationTask.Operation operation,
            String prompt,
            Map<String, Object> settings,
            List<String> inputAssetIds);
}
