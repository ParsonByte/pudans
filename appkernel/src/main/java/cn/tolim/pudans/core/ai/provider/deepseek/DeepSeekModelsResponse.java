package cn.tolim.pudans.core.ai.provider.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 移植自 deepseek4j 1.6.1 的模型目录响应。
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeepSeekModelsResponse(String object, List<DeepSeekModel> data) {

    public DeepSeekModelsResponse {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
