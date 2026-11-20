package cn.tolim.pudans.core.ai.provider.langchain4j;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LangChain4jChatServiceTest {

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final LangChain4jModelFactory modelFactory = mock(LangChain4jModelFactory.class);

    private final LangChain4jChatService service = new LangChain4jChatService(
            accountService, modelFactory, JsonUtils.mapper());

    @Test
    void chatsThroughTheNonNullBlockingModelApi() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var chatModel = mock(ChatModel.class);
        when(modelFactory.chatModel(
                        any(), eq(AiProvider.Protocol.OPENAI_CHAT), eq("gpt-4.1"), eq("sk-secret")))
                .thenReturn(chatModel);
        var captured = new AtomicReference<ChatRequest>();
        when(chatModel.chatAsync(any(ChatRequest.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return CompletableFuture.completedFuture(response());
        });

        var result = service.chat(
                        provider(AiProvider.Protocol.OPENAI_CHAT),
                        account(),
                        model("gpt-4.1", 2048),
                        AiProvider.Protocol.OPENAI_CHAT,
                        request())
                .block();

        assertThat(result).isNotNull();
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, null));
        assertThat(result.message().parts())
                .filteredOn(AiContentPart.Text.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                        assertThat(text.text()).isEqualTo("Hello"));
        assertThat(captured.get().modelName()).isEqualTo("gpt-4.1");
    }

    @Test
    void fallsBackToTheBlockingApiWhenAsyncIsNotSupported() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("key"));
        var chatModel = mock(ChatModel.class);
        when(modelFactory.chatModel(any(), eq(AiProvider.Protocol.GEMINI), eq("gemini-pro"), eq("key")))
                .thenReturn(chatModel);
        when(chatModel.chatAsync(any(ChatRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new AsyncNotSupportedException("no async")));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("fallback"))
                        .finishReason(FinishReason.STOP)
                        .build());

        var result = service.chat(
                        provider(AiProvider.Protocol.GEMINI),
                        account(),
                        model("gemini-pro", null),
                        AiProvider.Protocol.GEMINI,
                        request())
                .block();

        assertThat(result).isNotNull();
        assertThat(result.message().parts())
                .singleElement()
                .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                        assertThat(text.text()).isEqualTo("fallback"));
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void streamsMappedEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var chatModel = mock(StreamingChatModel.class);
        when(modelFactory.streamingChatModel(
                        any(), eq(AiProvider.Protocol.OPENAI_CHAT), eq("gpt-4.1"), eq("sk-secret")))
                .thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(publisher(new PartialResponse("Hel"), new CompleteResponse(response())));

        var events = service.streamChat(
                        provider(AiProvider.Protocol.OPENAI_CHAT),
                        account(),
                        model("gpt-4.1", 2048),
                        AiProvider.Protocol.OPENAI_CHAT,
                        request())
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).finishReason()).isEqualTo("stop");
        assertThat(events.get(1).usage()).isEqualTo(new AiTokenUsage(10, 20, 4, null));
    }

    @Test
    void rejectsInvalidProviderConfiguration() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("key"));
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setBaseUrl(null);

        StepVerifier.create(service.chat(
                        provider,
                        account(),
                        model("gpt-4.1", null),
                        AiProvider.Protocol.OPENAI_CHAT,
                        request()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(
                                cn.tolim.pudans.infra.exception.ApiException.class,
                                api -> assertThat(api.getCode())
                                        .isEqualTo(cn.tolim.pudans.core.ai.provider
                                                .AiProviderConfigSupport.CODE_INVALID)))
                .verify();
    }

    private static ChatResponse response() {
        return ChatResponse.builder()
                .id("chatcmpl-1")
                .modelName("gpt-4.1")
                .aiMessage(AiMessage.from("Hello"))
                .tokenUsage(OpenAiTokenUsage.builder()
                        .inputTokenCount(10)
                        .outputTokenCount(20)
                        .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                                .cachedTokens(4)
                                .build())
                        .build())
                .finishReason(FinishReason.STOP)
                .build();
    }

    private static Flow.Publisher<ChatModelStreamingEvent> publisher(ChatModelStreamingEvent... events) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {

            private int index;

            private boolean completed;

            @Override
            public void request(long demand) {
                var limit = Math.min(demand, events.length - index);
                for (var i = 0L; i < limit; i++) {
                    subscriber.onNext(events[index++]);
                }
                if (index == events.length && !completed) {
                    completed = true;
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {}
        });
    }
}