package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultMemoryExtractionGatewayTest {

    @Test
    void usesTheCompleteResponseWhenNoPartialTextArrives() {
        StreamingChatModel model = new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("[{\"layer\":\"L1\"}]"))
                        .build());
            }
        };

        StepVerifier.create(DefaultMemoryExtractionGateway.collect(model, "真实消息"))
                .assertNext(text -> assertThat(text).isEqualTo("[{\"layer\":\"L1\"}]"))
                .verifyComplete();
    }
}