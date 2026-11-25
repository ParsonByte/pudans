package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolDescriptor;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfiles;
import cn.tolim.pudans.core.ai.protocol.AiProtocols;
import cn.tolim.pudans.core.ai.protocol.chatcompletions.ChatCompletionsProfile;
import cn.tolim.pudans.core.ai.protocol.chatcompletions.StandardChatCompletionsProfile;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.exception.ApiException;
import io.agentscope.core.model.Model;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AgentChatModelFactory {

    private static final String API_KEY = "apiKey";

    private static final String FEATURE_DETAILS = "features";

    private static final ChatCompletionsProfile STANDARD_CHAT = new StandardChatCompletionsProfile();

    private final AiProtocols protocols;

    private final AiProviders providers;

    // 依据真实渠道协议构建 AgentScope 聊天模型。
    public Model create(AgentModelRoute route) {
        return create(route, null);
    }

    // 把运行级推理开关交给协议配置映射为渠道参数。
    public Model create(AgentModelRoute route, String reasoning) {
        var channel = route.channel();
        var upstream = requiredText(
                route.channelModel().upstreamModelKey(), "上游模型标识不能为空。");
        var apiKey = requiredText(
                route.credentials().get(API_KEY), "渠道缺少 apiKey 凭据。");
        var descriptor = protocol(channel.protocol(), channel.provider());
        var provider = providers.require(channel.provider());
        var protocol = descriptor.protocol();
        var profile = provider.protocolProfile(protocol);
        var bodyParams = protocol == AiProtocol.CHAT_COMPLETIONS
                ? AiProtocolProfiles.resolve(profile, ChatCompletionsProfile.class, STANDARD_CHAT)
                        .reasoningBodyParams(supports(route.model(), "reasoning"), reasoning)
                : Map.<String, Object>of();
        return protocols.requireChat(protocol.value())
                .create(
                        request(route, provider, apiKey, upstream, protocol, bodyParams),
                        profile);
    }

    private static AiChatProtocolRequest request(
            AgentModelRoute route,
            AiProvider provider,
            String apiKey,
            String upstream,
            AiProtocol protocol,
            Map<String, Object> bodyParams) {
        var channel = route.channel();
        var structured = supports(route.model(), "structured_output");
        var baseUrl = switch (protocol) {
            case CHAT_COMPLETIONS -> provider.chatCompletionsBaseUrl(channel.baseUrl());
            case MESSAGES -> provider.anthropicBaseUrl(channel.baseUrl());
            case RESPONSES -> provider.responsesBaseUrl(channel.baseUrl());
            case SYSTEM_ONE -> provider.systemOneBaseUrl(channel.baseUrl());
        };
        var path = switch (protocol) {
            case CHAT_COMPLETIONS -> "/chat/completions";
            case MESSAGES -> provider.messagesPath();
            case RESPONSES -> provider.responsesPath();
            case SYSTEM_ONE -> provider.systemOnePath();
        };
        var headers = switch (protocol) {
            case CHAT_COMPLETIONS -> Map.<String, String>of();
            case MESSAGES -> provider.messagesHeaders(apiKey);
            case RESPONSES -> provider.responsesHeaders();
            case SYSTEM_ONE -> provider.systemOneHeaders();
        };
        return new AiChatProtocolRequest(
                provider.id(),
                upstream,
                apiKey,
                baseUrl,
                path,
                headers,
                timeoutMs(channel),
                contextWindow(route.model()),
                route.model().maxOutputTokens(),
                structured,
                structured && supports(route.model(), "tools"),
                protocol == AiProtocol.CHAT_COMPLETIONS ? bodyParams : Map.of());
    }

    private AiProtocolDescriptor protocol(String protocol, String provider) {
        var requested = protocols.find(protocol);
        if (requested.isPresent()) {
            if (!requested.get().runtimeSupported()) {
                throw unsupported(requested.get());
            }
            return requested.get();
        }
        var fallback = providers.find(provider)
                .map(AiProvider::descriptor)
                .map(cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor::defaultProtocol)
                .flatMap(protocols::find);
        if (fallback.isEmpty()) {
            throw unsupported(protocol, provider);
        }
        if (!fallback.get().runtimeSupported()) {
            throw unsupported(fallback.get());
        }
        return fallback.get();
    }

    private static Integer contextWindow(AiModel model) {
        return model.contextWindow() != null && model.contextWindow() > 0
                ? model.contextWindow()
                : null;
    }

    private static boolean supports(AiModel model, String feature) {
        if (model.capabilities() == null) {
            return false;
        }
        if (Boolean.TRUE.equals(model.capabilities().get(feature))) {
            return true;
        }
        var details = model.capabilities().get("chat");
        if (!(details instanceof Map<?, ?> values)) {
            return false;
        }
        if (Boolean.TRUE.equals(values.get(feature))) {
            return true;
        }
        var features = values.get(FEATURE_DETAILS);
        return features instanceof List<?> list && list.contains(feature);
    }

    private static int timeoutMs(cn.tolim.pudans.contract.ai.AiChannel channel) {
        return channel.timeoutMs() == null || channel.timeoutMs() <= 0
                ? 60_000
                : channel.timeoutMs();
    }

    private static String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "agent-channel-route-incomplete", message);
        }
        return value.trim();
    }

    private static ApiException unsupported(AiProtocolDescriptor descriptor) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "agent-channel-protocol-unsupported",
                descriptor.protocol().displayName() + " 协议尚未接入聊天运行时。");
    }

    private static ApiException unsupported(String protocol, String provider) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "agent-channel-protocol-unsupported",
                "聊天渠道协议不受支持：" + protocol + "/" + provider + "。");
    }
}
