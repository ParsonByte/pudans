package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Closeable;
import org.junit.jupiter.api.Test;

class LangChain4jCompatibilityTest {

    @Test
    void loadsLangChain4jAndLegacyStorageSdkOnTheSameOkHttpLine() throws Exception {
        assertThat(Class.forName("com.upyun.RestManager")).isNotNull();
        assertThat(Class.forName("okhttp3.internal.Util")
                        .getMethod("closeQuietly", Closeable.class))
                .isNotNull();
        assertThat(Class.forName("dev.langchain4j.model.chat.StreamingChatModel")).isNotNull();
        assertThat(Class.forName("dev.langchain4j.model.openai.OpenAiStreamingChatModel"))
                .isNotNull();
        assertThat(Class.forName("dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel"))
                .isNotNull();
        assertThat(Class.forName("dev.langchain4j.model.anthropic.AnthropicStreamingChatModel"))
                .isNotNull();
        assertThat(Class.forName("dev.langchain4j.community.model.dashscope.QwenEmbeddingModel"))
                .isNotNull();
        assertThat(Class.forName("dev.langchain4j.mcp.client.DefaultMcpClient")).isNotNull();
        assertThat(Class.forName("dev.langchain4j.model.embedding.EmbeddingModel")).isNotNull();
        assertThat(Class.forName("com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesis"))
                .isNotNull();
    }
}