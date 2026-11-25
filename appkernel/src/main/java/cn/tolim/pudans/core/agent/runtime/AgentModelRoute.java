package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.contract.ai.AiChannel;
import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.AiModel;
import java.util.Map;
import java.util.Objects;

public record AgentModelRoute(
        AiModel model,
        AiChannelModel channelModel,
        AiChannel channel,
        Map<String, String> credentials) {

    public AgentModelRoute {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(channelModel, "channelModel");
        Objects.requireNonNull(channel, "channel");
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
    }
}
