package cn.tolim.pudans.core.ai.gateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiProtocolCodecRegistryTest {

    private final AiProtocolCodecRegistry registry = new AiProtocolCodecRegistry(new ObjectMapper());

    @Test
    void resolvesSupportedProtocols() {
        assertThat(registry.require(AiProvider.Protocol.OPENAI_CHAT).protocol())
                .isEqualTo(AiProvider.Protocol.OPENAI_CHAT);
        assertThat(registry.require(AiProvider.Protocol.ANTHROPIC).protocol())
                .isEqualTo(AiProvider.Protocol.ANTHROPIC);
    }

    @Test
    void rejectsUnsupportedProtocols() {
        assertThatThrownBy(() -> registry.require(AiProvider.Protocol.GEMINI))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registry.require(null))
                .isInstanceOf(ApiException.class);
    }
}
