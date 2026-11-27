package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.core.ai.AiModelDirectory;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AgentModelRouteResolver {

    private static final String CHAT = "chat";

    private final AiModelDirectory models;

    private final AiChannelModelRepository channelModels;

    private final AiChannels channels;

    // 按真实模型目录和启用渠道解析聊天路由。
    public Mono<AgentModelRoute> resolve(String workspaceId, UUID modelId) {
        return models
                .resolve(workspaceId, modelId == null ? null : modelId.toString(), CHAT)
                .flatMap(model -> channelModels
                        .findEnabledRoute(model.id())
                        .switchIfEmpty(Mono.error(routeMissing()))
                        .flatMap(channelModel -> channels
                                .get(channelModel.channelId())
                                .flatMap(channel -> channels
                                        .credentials(channel.id())
                                        .map(credentials -> new AgentModelRoute(
                                                model, channelModel, channel, credentials)))));
    }

    private static ApiException routeMissing() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "agent-model-channel-unavailable",
                "当前模型没有启用的聊天渠道。");
    }
}
