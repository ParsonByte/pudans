package cn.tolim.pudans.core.ai.harness.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class StructuredOutputServiceTest {

    @Mock
    ChatModel chatModel;

    private final StructuredOutputService service = new StructuredOutputService(JsonMapper.shared());

    @Test
    void usesNativeJsonSchemaWhenTheModelDeclaresSupport() {
        var schema = JsonSchema.builder()
                .name("Sentiment")
                .rootElement(JsonObjectSchema.builder()
                        .addStringProperty("label")
                        .addIntegerProperty("score")
                        .required("label", "score")
                        .build())
                .build();
        when(chatModel.supportedCapabilities())
                .thenReturn(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response("{\"label\":\"positive\",\"score\":9}"));

        var request = StructuredOutputRequest.of("Classify the sentiment.", "The battery lasts all day.")
                .withJsonSchema(schema);
        var result = service.generate(chatModel, request, Sentiment.class).block();

        assertThat(result).isEqualTo(new Sentiment("positive", 9));
        var sent = capturedRequest();
        assertThat(sent.responseFormat()).isNotNull();
        assertThat(sent.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(sent.responseFormat().jsonSchema()).isEqualTo(schema);
        assertThat(sent.messages()).hasSize(2);
        assertThat(sent.messages().getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) sent.messages().getFirst()).text())
                .isEqualTo("Classify the sentiment.");
        assertThat(sent.messages().get(1)).isInstanceOf(UserMessage.class);
    }

    @Test
    void fallsBackToADeterministicPromptAndExtractsFencedJson() {
        when(chatModel.supportedCapabilities()).thenReturn(Set.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response("Here is the result:\n```json\n{\"label\":\"neutral\",\"score\":5}\n```"));

        var result = service
                .generate(chatModel, StructuredOutputRequest.of("Classify.", "It was fine."), Sentiment.class)
                .block();

        assertThat(result).isEqualTo(new Sentiment("neutral", 5));
        var sent = capturedRequest();
        assertThat(sent.responseFormat()).isNull();
        var system = (SystemMessage) sent.messages().getFirst();
        assertThat(system.text())
                .contains("Classify.")
                .contains("JSON Schema")
                .contains("\"label\"")
                .contains("\"score\"");
        assertThat(sent.messages().get(1)).isInstanceOf(UserMessage.class);
    }

    @Test
    void extractsJsonFromSurroundingProse() {
        when(chatModel.supportedCapabilities()).thenReturn(Set.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response("The answer is {\"label\":\"negative\",\"score\":2}."));

        var result = service
                .generate(chatModel, StructuredOutputRequest.of("Classify.", "Terrible."), Sentiment.class)
                .block();

        assertThat(result).isEqualTo(new Sentiment("negative", 2));
    }

    @Test
    void raisesAClearErrorForInvalidJson() {
        when(chatModel.supportedCapabilities()).thenReturn(Set.of());
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("I cannot answer that."));

        StepVerifier.create(service.generate(
                        chatModel, StructuredOutputRequest.of("Classify.", "?"), Sentiment.class))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(StructuredOutputException.class)
                        .hasMessageContaining(Sentiment.class.getName()))
                .verify();
    }

    @Test
    void raisesAClearErrorWhenTheModelReturnsNoText() {
        when(chatModel.supportedCapabilities()).thenReturn(Set.of());
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("   "));

        StepVerifier.create(service.generate(
                        chatModel, StructuredOutputRequest.of("Classify.", "?"), Sentiment.class))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(StructuredOutputException.class)
                        .hasMessageContaining("未返回文本内容"))
                .verify();
    }

    @Test
    void mergesTheFallbackInstructionIntoAnExistingSystemMessage() {
        when(chatModel.supportedCapabilities()).thenReturn(Set.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response("{\"label\":\"positive\",\"score\":8}"));

        var request = StructuredOutputRequest.of(
                List.of(SystemMessage.from("Classify."), UserMessage.from("Great.")));
        var result = service.generate(chatModel, request, Sentiment.class).block();

        assertThat(result).isEqualTo(new Sentiment("positive", 8));
        var sent = capturedRequest();
        assertThat(sent.messages()).hasSize(2);
        var system = (SystemMessage) sent.messages().getFirst();
        assertThat(system.text()).startsWith("Classify.\n\n").contains("JSON Schema");
    }

    @Test
    void rejectsARequestWithoutSystemPromptOrMessages() {
        assertThatThrownBy(() -> service
                        .generate(chatModel, StructuredOutputRequest.of(List.of()), Sentiment.class)
                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("系统提示或消息");
    }

    private ChatRequest capturedRequest() {
        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        return captor.getValue();
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    record Sentiment(String label, int score) {}
}