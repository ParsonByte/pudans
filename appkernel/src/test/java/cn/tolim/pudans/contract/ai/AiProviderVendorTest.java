package cn.tolim.pudans.contract.ai;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class AiProviderVendorTest {

    @Test
    void serializesVendorAndHidesTheInternalProtocol() {
        var provider = new AiProvider();
        provider.getSpec().setDisplayName("OpenAI");
        provider.getSpec().setVendor(AiProvider.Vendor.OPENAI);
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_RESPONSES);

        var json = JsonUtils.objectToJson(provider);

        assertThat(json).contains("\"vendor\":\"OPENAI\"").doesNotContain("\"protocol\"");
        assertThat(provider.getSpec().getProtocol()).isEqualTo(AiProvider.Protocol.OPENAI_CHAT);
    }

    @Test
    void deserializesALegacyProtocolWhenVendorIsMissing() {
        var provider = JsonUtils.jsonToObject(
                "{\"spec\":{\"displayName\":\"Legacy\",\"protocol\":\"OPENAI_RESPONSES\"}}",
                AiProvider.class);

        assertThat(provider.getSpec().getVendor()).isEqualTo(AiProvider.Vendor.OPENAI);
        assertThat(provider.getSpec().getProtocol())
                .isEqualTo(AiProvider.Protocol.OPENAI_RESPONSES);
    }
}