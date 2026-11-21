package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class AiProviderHttpSupportTest {

    @Test
    void joinsEndpointAndNormalizesSlashes() {
        var provider = AiProviderAdapterTestFixtures.provider(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setBaseUrl("https://api.example.com/");

        assertThat(AiProviderHttpSupport.endpoint(provider, "/v1/chat/completions"))
                .isEqualTo("https://api.example.com/v1/chat/completions");
        assertThat(AiProviderHttpSupport.endpoint(provider, "v1/messages"))
                .isEqualTo("https://api.example.com/v1/messages");
    }

    @Test
    void preservesBasePathWhenJoiningEndpoint() {
        var provider = AiProviderAdapterTestFixtures.provider(AiProvider.Protocol.ANTHROPIC);
        provider.getSpec().setBaseUrl("https://gateway.example.com/proxy/");

        assertThat(AiProviderHttpSupport.endpoint(provider, "/v1/messages"))
                .isEqualTo("https://gateway.example.com/proxy/v1/messages");
    }

    @Test
    void rejectsMissingBaseUrl() {
        var provider = AiProviderAdapterTestFixtures.provider(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setBaseUrl(null);

        assertThatThrownBy(() -> AiProviderHttpSupport.endpoint(provider, "/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base URL");
    }

    @Test
    void rejectsUnsupportedBaseUrlScheme() {
        var provider = AiProviderAdapterTestFixtures.provider(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setBaseUrl("file:///etc/passwd");

        assertThatThrownBy(() -> AiProviderHttpSupport.endpoint(provider, "/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    @Test
    void appliesProviderHeadersWithoutOverwritingThemWithNull() {
        var provider = AiProviderAdapterTestFixtures.provider(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().getHeaders().put("X-Org-Id", "org-1");
        provider.getSpec().getHeaders().put("X-Empty", null);
        var headers = new HttpHeaders();

        AiProviderHttpSupport.addProviderHeaders(headers, provider);

        assertThat(headers.getFirst("X-Org-Id")).isEqualTo("org-1");
        assertThat(headers.containsHeader("X-Empty")).isFalse();
    }

    @Test
    void rejectsNonObjectResponseBody() {
        assertThatThrownBy(() -> AiProviderHttpSupport.readObject(JsonUtils.mapper(), "[]", "OpenAI Chat"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("JSON 对象");
    }

    @Test
    void rejectsMalformedResponseBody() {
        assertThatThrownBy(() -> AiProviderHttpSupport.readObject(JsonUtils.mapper(), "{", "OpenAI Chat"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("无法解析");
    }
}
