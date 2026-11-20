package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.model.dashscope.QwenScoringModel;
import dev.langchain4j.model.scoring.ScoringModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class LangChain4jScoringModelFactory {

    private final WebClient webClient;

    private final ObjectMapper mapper;

    public LangChain4jScoringModelFactory(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient, ObjectMapper mapper) {
        this.webClient = webClient;
        this.mapper = mapper;
    }

    public boolean supports(AiProvider.Protocol protocol) {
        return protocol == AiProvider.Protocol.QIANWEN || protocol == AiProvider.Protocol.OPENAI_CHAT;
    }

    public ScoringModel create(AiProvider provider, String apiKey, String modelCode) {
        var protocol = provider == null || provider.getSpec() == null
                ? null
                : provider.getSpec().getProtocol();
        if (protocol == AiProvider.Protocol.QIANWEN) {
            var builder = QwenScoringModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelCode);
            var baseUrl = provider.getSpec().getBaseUrl();
            if (StringUtils.hasText(baseUrl)) {
                builder.baseUrl(qwenBaseUrl(baseUrl));
            }
            return builder.build();
        }
        if (protocol == AiProvider.Protocol.OPENAI_CHAT) {
            return new OpenAiCompatibleScoringModel(webClient, mapper, provider, apiKey, modelCode);
        }
        throw new IllegalArgumentException("暂不支持该重排协议：" + protocol + "。");
    }

    static String qwenBaseUrl(String baseUrl) {
        var normalized = baseUrl.strip().replaceAll("/+$", "");
        var compatible = normalized.indexOf("/compatible-mode/");
        if (compatible > 0) {
            normalized = normalized.substring(0, compatible);
        }
        return normalized.endsWith("/api/v1") ? normalized : normalized + "/api/v1";
    }
}