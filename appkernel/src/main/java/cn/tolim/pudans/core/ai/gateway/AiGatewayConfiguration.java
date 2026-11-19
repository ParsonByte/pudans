package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.core.ai.gateway.agent.AiAgentAdapterRegistry;
import cn.tolim.pudans.core.ai.gateway.agent.ClaudeCodeAgentAdapter;
import cn.tolim.pudans.core.ai.gateway.agent.OpenAiDefaultAgentAdapter;
import cn.tolim.pudans.core.ai.gateway.protocol.AiResponseEncoders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiGatewayConfiguration {

    @Bean
    public AiResponseEncoders aiResponseEncoders(ObjectMapper mapper) {
        return new AiResponseEncoders(mapper);
    }

    @Bean
    public AiAgentAdapterRegistry aiAgentAdapterRegistry() {
        var fallback = new OpenAiDefaultAgentAdapter();
        return new AiAgentAdapterRegistry(List.of(new ClaudeCodeAgentAdapter(), fallback), fallback);
    }
}
