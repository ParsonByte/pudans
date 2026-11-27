package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiQuotaReservation;
import cn.tolim.pudans.core.agent.memory.AgentMemoryContext;
import cn.tolim.pudans.core.agent.tool.GenerateMediaTool;
import cn.tolim.pudans.core.ai.quota.AiPricing;
import cn.tolim.pudans.core.ai.quota.AiQuotas;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentToolCallEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentToolCallRepository;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DefaultAgentRuntimeTest {

    @Mock
    AgentModelRouteResolver routes;

    @Mock
    GenerateMediaTool mediaTool;

    @Mock
    AgentRunEvents events;

    @Mock
    AgentRunRepository runs;

    @Mock
    AgentMessageRepository messages;

    @Mock
    AgentToolCallRepository toolCalls;

    @Mock
    AgentApprovalRepository approvals;

    @Mock
    AiQuotas quotas;

    @Mock
    AiPricing pricing;

    @Mock
    AgentChatModelFactory modelFactory;

    @Mock
    AgentMemoryContext memoryContext;

    private DefaultAgentRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new DefaultAgentRuntime(
                routes,
                mediaTool,
                events,
                runs,
                messages,
                toolCalls,
                approvals,
                quotas,
                pricing,
                modelFactory,
                memoryContext);
    }

    @Test
    void cancelReturnsFalseWhenNoLocalHandleExists() {
        StepVerifier.create(runtime.cancel(context()))
                .expectNext(false)
                .verifyComplete();

        verifyNoInteractions(runs, events, quotas);
    }

    @Test
    void resumeReturnsFalseWhenNoLocalApprovalExists() {
        StepVerifier.create(runtime.resume(context(), UUID.randomUUID(), true))
                .expectNext(false)
                .verifyComplete();

        verifyNoInteractions(runs, events, quotas);
    }

    @Test
    void propagatesModelRouteFailuresBeforeReservingQuota() {
        var context = context();
        when(routes.resolve(context.workspaceId(), context.modelId()))
                .thenReturn(Mono.error(new IllegalStateException("route unavailable")));

        StepVerifier.create(runtime.start(context))
                .expectErrorSatisfies(error -> assertThat(error)
                        .hasMessage("route unavailable"))
                .verify();

        verifyNoInteractions(runs, events, quotas);
    }

    @Test
    void buildsTheModelBeforeReservingQuota() {
        var context = context();
        when(routes.resolve(context.workspaceId(), context.modelId()))
                .thenReturn(Mono.just(route()));
        when(memoryContext.recall(anyString(), anyString(), anyString())).thenReturn(Mono.just(""));
        when(modelFactory.create(any(), any())).thenThrow(new IllegalStateException("model invalid"));

        StepVerifier.create(runtime.start(context))
                .expectErrorSatisfies(error -> assertThat(error)
                        .hasMessage("model invalid"))
                .verify();

        verifyNoInteractions(quotas);
    }

    @Test
    void completesARealStreamedModelTurn() throws Exception {
        var context = context();
        var run = givenBackend(context);
        when(modelFactory.create(any(), any())).thenReturn(new CompletedModel());
        var completed = new CountDownLatch(1);
        var published = new java.util.concurrent.CopyOnWriteArrayList<String>();
        when(events.publish(any(), any(), anyString(), any())).thenAnswer(invocation -> {
            var type = invocation.getArgument(2, String.class);
            return Mono.fromRunnable(() -> {
                published.add(type);
                if (AgentRunEvents.RUN_COMPLETED.equals(type)) {
                    completed.countDown();
                }
            });
        });

        runtime.start(context).block();

        assertThat(completed.await(5, TimeUnit.SECONDS))
                .as("published=%s status=%s error=%s", published, run.getStatus(), run.getErrorMessage())
                .isTrue();
        var messageCaptor = ArgumentCaptor.forClass(cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity.class);
        verify(messages).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContentJson())
                .contains("\"thinking\"")
                .contains("\"text\":\"model reasoning\"");
        assertThat(run.getStatus()).isEqualTo("completed");
    }

    @Test
    void pausesForToolApprovalAndContinuesAfterTheDecision() throws Exception {
        var context = context();
        var run = givenBackend(context);
        when(modelFactory.create(any(), any())).thenReturn(new ApprovalModel());
        when(mediaTool.execute(any(), any())).thenReturn(Mono.just(Map.of("taskId", "task-1")));
        when(approvals.findFirstByRunIdAndToolCallIdAndStatus(any(), any(), any()))
                .thenReturn(Mono.empty());
        when(approvals.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(toolCalls.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(toolCalls.findById(any(UUID.class))).thenReturn(Mono.just(new AgentToolCallEntity()));
        var approvalRequested = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        var approvalId = new java.util.concurrent.atomic.AtomicReference<UUID>();
        when(events.publish(any(), any(), anyString(), any())).thenAnswer(invocation -> {
            var type = invocation.getArgument(2, String.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) invocation.getArgument(3);
            return Mono.fromRunnable(() -> {
                if (AgentRunEvents.TOOL_APPROVAL_REQUIRED.equals(type)) {
                    approvalId.set((UUID) data.get("approvalId"));
                    approvalRequested.countDown();
                }
                if (AgentRunEvents.RUN_COMPLETED.equals(type)) {
                    completed.countDown();
                }
            });
        });

        runtime.start(context).block();

        assertThat(approvalRequested.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(approvalId.get()).isNotNull();
        assertThat(runtime.resume(context, approvalId.get(), true).block()).isTrue();
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mediaTool).execute(any(), any());
        assertThat(run.getStatus()).isEqualTo("completed");
    }

    @Test
    void reportsCachedTokensInTheCompletedUsage() throws Exception {
        var context = context();
        givenBackend(context);
        when(modelFactory.create(any(), any())).thenReturn(new CachedTokenModel());
        var completed = new CountDownLatch(1);
        var completedData = new AtomicReference<Map<String, Object>>();
        when(events.publish(any(), any(), anyString(), any())).thenAnswer(invocation -> {
            var type = invocation.getArgument(2, String.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) invocation.getArgument(3);
            return Mono.fromRunnable(() -> {
                if (AgentRunEvents.RUN_COMPLETED.equals(type)) {
                    completedData.set(data);
                    completed.countDown();
                }
            });
        });

        runtime.start(context).block();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        @SuppressWarnings("unchecked")
        var usage = (Map<String, Object>) completedData.get().get("usage");
        assertThat(usage).containsEntry("cachedTokens", 40L);
    }

    @Test
    void rejectsAToolWithoutExecutingItAndFailsTheRun() throws Exception {
        var context = context();
        var run = givenBackend(context);
        when(modelFactory.create(any(), any())).thenReturn(new ApprovalModel());
        when(approvals.findFirstByRunIdAndToolCallIdAndStatus(any(), any(), any()))
                .thenReturn(Mono.empty());
        when(approvals.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(toolCalls.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(toolCalls.findById(any(UUID.class))).thenReturn(Mono.just(new AgentToolCallEntity()));
        var approvalRequested = new CountDownLatch(1);
        var failed = new CountDownLatch(1);
        var approvalId = new java.util.concurrent.atomic.AtomicReference<UUID>();
        when(events.publish(any(), any(), anyString(), any())).thenAnswer(invocation -> {
            var type = invocation.getArgument(2, String.class);
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) invocation.getArgument(3);
            return Mono.fromRunnable(() -> {
                if (AgentRunEvents.TOOL_APPROVAL_REQUIRED.equals(type)) {
                    approvalId.set((UUID) data.get("approvalId"));
                    approvalRequested.countDown();
                }
                if (AgentRunEvents.RUN_FAILED.equals(type)) {
                    failed.countDown();
                }
            });
        });

        runtime.start(context).block();

        assertThat(approvalRequested.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runtime.resume(context, approvalId.get(), false).block()).isTrue();
        assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mediaTool, never()).execute(any(), any());
        assertThat(run.getStatus()).isEqualTo("failed");
        assertThat(run.getErrorCode()).isEqualTo("agent-tools-denied");
    }

    private AgentRunEntity givenBackend(AgentRunContext context) {
        var route = route();
        var run = run(context.runId(), context.sessionId());
        var reservation = new AiQuotaReservation(
                UUID.randomUUID(),
                "user",
                context.ownerId(),
                null,
                context.runId(),
                null,
                BigDecimal.ONE,
                null,
                1,
                null,
                0,
                null,
                null,
                null,
                "reserved",
                "agent-run:" + context.runId(),
                null,
                null,
                null);
        when(routes.resolve(context.workspaceId(), context.modelId())).thenReturn(Mono.just(route));
        when(memoryContext.recall(anyString(), anyString(), anyString())).thenReturn(Mono.just(""));
        lenient().when(memoryContext.record(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(pricing.quoteTokens(any(), any(), any()))
                .thenReturn(Mono.just(new AiPricing.PriceQuote(
                        null, "token", BigDecimal.ONE, BigDecimal.ONE, false)));
        when(quotas.reserve(any(), any(), any())).thenReturn(Mono.just(reservation));
        when(quotas.settle(any(), any(), anyInt(), anyInt())).thenReturn(Mono.just(reservation));
        when(runs.findById(context.runId())).thenReturn(Mono.just(run));
        when(runs.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient().when(messages.save(any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        return run;
    }

    private static AgentModelRoute route() {
        var modelId = UUID.randomUUID();
        var channelId = UUID.randomUUID();
        var model = new cn.tolim.pudans.contract.ai.AiModel(
                modelId,
                "custom/model",
                "Custom",
                null,
                java.util.Map.of(),
                32_000,
                4_096,
                java.util.Map.of(),
                "active",
                null,
                null);
        var channelModel = new cn.tolim.pudans.contract.ai.AiChannelModel(
                UUID.randomUUID(),
                channelId,
                modelId,
                "upstream-model",
                1,
                100,
                true,
                java.util.Map.of(),
                java.util.Map.of(),
                null,
                null);
        var channel = new cn.tolim.pudans.contract.ai.AiChannel(
                channelId,
                "workspace",
                "custom",
                "chat-completions",
                "https://api.example.com",
                null,
                true,
                java.util.Map.of(),
                "active",
                30_000,
                10,
                100,
                null,
                java.util.Map.of(),
                null,
                null);
        return new AgentModelRoute(model, channelModel, channel, java.util.Map.of("apiKey", "secret"));
    }

    private static AgentRunEntity run(UUID runId, UUID sessionId) {
        var run = new AgentRunEntity();
        run.setId(runId);
        run.setSessionId(sessionId);
        run.setStatus("queued");
        run.setCreatedAt(OffsetDateTime.now());
        return run;
    }

    private static AgentRunContext context() {
        return new AgentRunContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                Set.of("agent-user"),
                "hello");
    }

    private static final class CompletedModel implements StreamingChatModel {

        @Override
        public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
            return publisher(new PartialResponse("hello"), new CompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text("hello")
                            .thinking("model reasoning")
                            .build())
                    .tokenUsage(new TokenUsage(3, 2))
                    .build()));
        }
    }

    private static final class CachedTokenModel implements StreamingChatModel {

        @Override
        public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
            var usage = OpenAiTokenUsage.builder()
                    .inputTokenCount(100)
                    .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                            .cachedTokens(40)
                            .build())
                    .outputTokenCount(20)
                    .totalTokenCount(120)
                    .build();
            return publisher(new CompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .tokenUsage(usage)
                    .build()));
        }
    }

    private static final class ApprovalModel implements StreamingChatModel {

        private final java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
            if (calls.getAndIncrement() == 0) {
                return publisher(new CompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                        .id("call-1")
                                        .name(GenerateMediaTool.NAME)
                                        .arguments("{\"media_type\":\"image\",\"model_id\":\"" + UUID.randomUUID()
                                                + "\",\"prompt\":\"a real prompt\"}")
                                        .build()))
                                .build())
                        .tokenUsage(new TokenUsage(3, 2))
                        .build()));
            }
            return publisher(new PartialResponse("done"), new CompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .tokenUsage(new TokenUsage(4, 2))
                    .build()));
        }
    }

    private static Flow.Publisher<ChatModelStreamingEvent> publisher(ChatModelStreamingEvent... events) {
        return subscriber -> {
            var publisher = new SubmissionPublisher<ChatModelStreamingEvent>();
            publisher.subscribe(subscriber);
            for (var event : events) {
                publisher.submit(event);
            }
            publisher.close();
        };
    }
}
