package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiProtocolCodecRegistry {

    private static final String CODE_UNSUPPORTED = "ai-gateway-protocol-unsupported";

    private final Map<AiProvider.Protocol, AiProtocolCodec> codecs;

    public AiProtocolCodecRegistry(ObjectMapper mapper) {
        var registered = new EnumMap<AiProvider.Protocol, AiProtocolCodec>(AiProvider.Protocol.class);
        registered.put(AiProvider.Protocol.OPENAI_CHAT, new OpenAiChatCodec(mapper));
        registered.put(AiProvider.Protocol.ANTHROPIC, new AnthropicMessagesCodec(mapper));
        this.codecs = Map.copyOf(registered);
    }

    public AiProtocolCodec require(AiProvider.Protocol protocol) {
        var codec = protocol == null ? null : codecs.get(protocol);
        if (codec == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_UNSUPPORTED, "不支持的网关协议：" + protocol);
        }
        return codec;
    }
}
