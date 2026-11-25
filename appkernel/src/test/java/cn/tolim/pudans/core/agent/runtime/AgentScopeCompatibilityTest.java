package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.model.transport.OkHttpTransport;
import java.io.Closeable;
import org.junit.jupiter.api.Test;

class AgentScopeCompatibilityTest {

    @Test
    void loadsAgentScopeAndLegacyStorageSdkOnTheSameOkHttpLine() throws Exception {
        assertThat(Class.forName("com.upyun.RestManager")).isNotNull();
        assertThat(Class.forName("okhttp3.internal.Util")
                        .getMethod("closeQuietly", Closeable.class))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.core.ReActAgent")).isNotNull();
        assertThat(Class.forName("io.agentscope.harness.agent.HarnessAgent")).isNotNull();
        assertThat(Class.forName("io.agentscope.extensions.model.openai.OpenAIChatModel"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.extensions.model.anthropic.AnthropicChatModel"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.extensions.model.dashscope.DashScopeChatModel"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.core.embedding.EmbeddingModel"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.core.embedding.openai.OpenAITextEmbedding"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.core.rag.knowledge.SimpleKnowledge"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.core.memory.reme.ReMeLongTermMemory"))
                .isNotNull();
        assertThat(Class.forName("io.agentscope.extensions.sandbox.e2b.E2bSandboxClient"))
                .isNotNull();
        assertThat(Class.forName("com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesis"))
                .isNotNull();
        var transport = new OkHttpTransport();
        try {
            assertThat(transport.getClient()).isNotNull();
        } finally {
            transport.close();
        }
    }
}
