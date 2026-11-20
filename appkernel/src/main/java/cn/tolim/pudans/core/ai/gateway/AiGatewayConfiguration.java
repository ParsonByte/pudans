package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.core.ai.gateway.agent.AiAgentAdapterRegistry;
import cn.tolim.pudans.core.ai.gateway.agent.ClaudeCodeAgentAdapter;
import cn.tolim.pudans.core.ai.gateway.agent.OpenAiDefaultAgentAdapter;
import cn.tolim.pudans.core.ai.gateway.protocol.AiResponseEncoders;
import cn.tolim.pudans.core.ai.gateway.protocol.GeminiGatewayCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiGatewayConfiguration {

    @Bean
    public GeminiGatewayCodec geminiGatewayCodec(ObjectMapper mapper) {
        return new GeminiGatewayCodec(mapper);
    }

    @Bean
    public AiResponseEncoders aiResponseEncoders(ObjectMapper mapper, GeminiGatewayCodec geminiCodec) {
        return new AiResponseEncoders(mapper, geminiCodec);
    }

    @Bean
    public AiAgentAdapterRegistry aiAgentAdapterRegistry() {
        var fallback = new OpenAiDefaultAgentAdapter();
        return new AiAgentAdapterRegistry(List.of(new ClaudeCodeAgentAdapter(), fallback), fallback);
    }
}
