package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

public interface AiProvider {

    Pattern VERSION_PATH = Pattern.compile(".*/v\\d+$");

    AiProviderDescriptor descriptor();

    Mono<List<AiProviderModel>> discover(
            AiChannelEntity channel, Map<String, String> credentials);

    default AiProtocolProfile protocolProfile(AiProtocol protocol) {
        return null;
    }

    default boolean acceptsModel(String modelId) {
        return true;
    }

    // 声明模型是否能通过模型列表接口校验。
    default boolean verifiesModelInList(String modelId) {
        return true;
    }

    default boolean supportsOpenAiCompatibleEmbedding() {
        return false;
    }

    default String embeddingBaseUrl(String baseUrl) {
        return resolveBaseUrl(baseUrl);
    }

    default String chatCompletionsBaseUrl(String baseUrl) {
        return resolveBaseUrl(baseUrl);
    }

    // LangChain4j 会在版本化 Base URL 后追加 /messages。
    default String anthropicBaseUrl(String baseUrl) {
        var normalized = resolveBaseUrl(baseUrl);
        return VERSION_PATH.matcher(normalized).matches() ? normalized : normalized + "/v1";
    }

    default Map<String, String> messagesHeaders(String apiKey) {
        return Map.of(
                "x-api-key", apiKey,
                "anthropic-version", "2023-06-01");
    }

    default String responsesBaseUrl(String baseUrl) {
        return resolveBaseUrl(baseUrl);
    }

    default String modelsUrl(String baseUrl) {
        return resolveBaseUrl(baseUrl) + "/models";
    }

    default String modelsUrlForChannel(AiChannelEntity channel) {
        return modelsUrl(channel.getBaseUrl());
    }

    default Map<String, String> responsesHeaders() {
        return Map.of();
    }

    default String systemOneBaseUrl(String baseUrl) {
        return withoutVersion(resolveBaseUrl(baseUrl));
    }

    default String systemOnePath() {
        return "/v1/systemone";
    }

    default Map<String, String> systemOneHeaders() {
        return Map.of();
    }

    default String id() {
        return descriptor().provider();
    }

    default String resolveBaseUrl(AiChannelEntity channel) {
        return resolveBaseUrl(channel.getBaseUrl());
    }

    default String resolveBaseUrl(String baseUrl) {
        var value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : descriptor().defaultBaseUrl();
        return stripTrailingSlash(value);
    }

    static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    static String withoutVersion(String value) {
        var normalized = stripTrailingSlash(value);
        return VERSION_PATH.matcher(normalized).matches()
                ? normalized.substring(0, normalized.lastIndexOf('/'))
                : normalized;
    }
}
