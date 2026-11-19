package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiChatMode;
import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiChatModeService;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseAcl;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalService;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiChatServiceMemoryTaskTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    AiSettingService settingService;

    @Mock
    AiChatModeService chatModeService;

    @Mock
    AiTeamService teamService;

    @Mock
    AiConversationService conversationService;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    KnowledgeBaseAcl knowledgeBaseAcl;

    @Mock
    KnowledgeBaseRetrievalService retrievalService;

    @Mock
    AiMemoryContextBuilder memoryContextBuilder;

    @Mock
    AiGatewayService gatewayService;

    @Mock
    AiEmbeddingService embeddingService;

    @Mock
    MemoryTaskScheduler memoryTaskScheduler;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(
                settingService,
                chatModeService,
                teamService,
                conversationService,
                client,
                knowledgeBaseAcl,
                retrievalService,
                embeddingService,
                memoryContextBuilder,
                gatewayService,
                memoryTaskScheduler);
    }

    @Test
    void completeEnqueuesAnL1TaskForTheAssistantMessage() {
        stubPrepare(true);
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(service.complete(command()))
                .assertNext(result -> {
                    assertThat(result.conversationId()).isEqualTo(7L);
                    assertThat(result.messageId()).isEqualTo("21");
                })
                .verifyComplete();

        verify(memoryTaskScheduler).enqueueExtraction(conversation(), "user-1", 21L);
    }

    @Test
    void streamEnqueuesAnL1TaskWhenTheAssistantMessageCompletes() {
        stubPrepare(true);
        when(gatewayService.stream(any())).thenReturn(Flux.just(
                new AiGatewayEvent.Meta(gatewayMeta()),
                new AiGatewayEvent.Done("21", "stop", false)));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(service.stream(command()))
                .assertNext(event -> assertThat(event).isInstanceOf(AiChatStreamEvent.Meta.class))
                .assertNext(event -> assertThat(event).isInstanceOf(AiChatStreamEvent.Done.class))
                .verifyComplete();

        verify(memoryTaskScheduler).enqueueExtraction(any(), anyString(), anyLong());
    }

    @Test
    void completeSkipsTheTaskWhenExtractionIsDisabled() {
        stubPrepare(false);
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(assistantMessage()));

        StepVerifier.create(service.complete(command()))
                .assertNext(result -> assertThat(result.messageId()).isEqualTo("21"))
                .verifyComplete();

        verifyNoInteractions(memoryTaskScheduler);
    }

    @Test
    void completeIgnoresAnEnqueueFailure() {
        stubPrepare(true);
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("队列不可用")));

        StepVerifier.create(service.complete(command()))
                .assertNext(result -> assertThat(result.messageId()).isEqualTo("21"))
                .verifyComplete();

        verify(memoryTaskScheduler).enqueueExtraction(any(), anyString(), anyLong());
    }

    private void stubPrepare(boolean extractionEnabled) {
        when(settingService.spec()).thenReturn(Mono.just(setting(extractionEnabled)));
        when(chatModeService.get("fast")).thenReturn(Mono.just(mode()));
        when(teamService.requireAgent("default", "user-1")).thenReturn(Mono.just(agent()));
        when(conversationService.createOrGet(any())).thenReturn(Mono.just(conversation()));
        when(memoryContextBuilder.build(any(), anyString(), anyInt(), anyInt())).thenReturn(Mono.empty());
        when(conversationService.appendUserMessage(any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(Mono.just(userMessage()));
        when(conversationService.markPending(any(), anyInt())).thenReturn(Mono.just(2));
    }

    private static AiChatCommand command() {
        return new AiChatCommand(
                "user-1",
                Set.of("user"),
                null,
                new AiChatCompletionRequest(
                        null,
                        "fast",
                        null,
                        null,
                        null,
                        null,
                        List.of(new AiChatCompletionRequest.Message("user", "你好")),
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private static AiSetting.Spec setting(boolean extractionEnabled) {
        var spec = new AiSetting().getSpec();
        spec.setDefaultChatMode("fast");
        spec.getMemory().setExtractionEnabled(extractionEnabled);
        return spec;
    }

    private static AiChatMode mode() {
        var mode = new AiChatMode();
        var metadata = new Metadata();
        metadata.setName("fast");
        mode.setMetadata(metadata);
        mode.getSpec().setDisplayName("快速模式");
        mode.getSpec().setRouteGroup("fast-group");
        return mode;
    }

    private static AiAgent agent() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("agent-a");
        agent.setMetadata(metadata);
        agent.getSpec().setTeam("team-a");
        agent.getSpec().setDisplayName("默认 Agent");
        agent.getSpec().setPrompt("你是一个助手");
        return agent;
    }

    private static Conversation conversation() {
        return new Conversation(
                7L,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                null,
                "fast",
                "fast-group",
                "ACTIVE",
                null,
                null,
                0,
                0L,
                NOW,
                NOW,
                NOW);
    }

    private static Message userMessage() {
        return new Message(
                10L, 7L, "default", "user-1", "team-a", "agent-a", "session-1", null, "req-1",
                1, 0, "user", "你好", "TEXT", null, null, NOW);
    }

    private static Message assistantMessage() {
        return new Message(
                21L, 7L, "default", "user-1", "team-a", "agent-a", "session-1", null, "req-1",
                1, 1, "assistant", "回答", "TEXT", 2, "gpt-test", NOW);
    }

    private static AiGatewayResult gatewayResult() {
        return new AiGatewayResult(
                new AiChatResponse(
                        "resp-1",
                        "gpt-test",
                        new AiChatMessage("assistant", List.of(new AiContentPart.Text("回答", null))),
                        new AiTokenUsage(1, 2, null, null),
                        "stop"),
                gatewayMeta(),
                new AiTokenUsage(1, 2, null, null),
                BigDecimal.ONE,
                "PROVIDER");
    }

    private static AiGatewayMeta gatewayMeta() {
        return new AiGatewayMeta(
                "req-1",
                7L,
                "fast",
                "fast-group",
                "provider-a",
                "account-a",
                "gpt-test",
                AiModel.Tier.CHEAP,
                "team-a",
                "agent-a",
                "session-1");
    }
}
