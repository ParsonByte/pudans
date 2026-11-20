package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiChatMode;
import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.core.extension.ai.AiHook;
import cn.tolim.pudans.core.ai.config.AiChatModeService;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.library.command.SlashCommandService;
import cn.tolim.pudans.core.ai.library.hook.HookService;
import cn.tolim.pudans.core.ai.library.mcp.McpServerService;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayInvocation;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.observability.HarnessObservability;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentRuntime;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentTools;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkitFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseAcl;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalService;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    StorageService storageService;

    @Mock
    AiAgentContextService agentContextService;

    @Mock
    AiChatToolService toolService;

    @Mock
    SlashCommandService slashCommandService;

    @Mock
    HookService hookService;

    @Mock
    cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore journalStore;

    @Mock
    cn.tolim.pudans.core.ai.harness.agent.AgentStateStore stateStore;

    @Mock
    SubagentToolFactory subagentToolFactory;

    @Mock
    HarnessToolkitFactory harnessToolkitFactory;

    @Mock
    McpServerService mcpServerService;

    @Mock
    HarnessObservability harnessObservability;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(journalStore.open(any()))
                .thenReturn(Mono.just(new cn.tolim.pudans.core.ai.harness.journal.InMemoryThreadJournal()));
        org.mockito.Mockito.lenient()
                .when(stateStore.load(anyString(), anyString()))
                .thenReturn(Mono.just(new cn.tolim.pudans.core.ai.harness.agent.AgentState()));
        org.mockito.Mockito.lenient()
                .when(stateStore.save(anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(subagentToolFactory.create(any(), any()))
                .thenReturn(Mono.just(new SubagentTools(new SubagentRuntime())));
        org.mockito.Mockito.lenient()
                .when(harnessToolkitFactory.create(any(), any(), anyBoolean(), any(), any()))
                .thenAnswer(invocation -> new HarnessToolkit().fallbackHandler(
                        invocation.getArgument(1, HarnessToolkit.ToolHandler.class)));
        org.mockito.Mockito.lenient()
                .when(mcpServerService.decorateToolkit(any(), any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
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
                memoryTaskScheduler,
                storageService,
                agentContextService,
                toolService,
                slashCommandService,
                hookService,
                journalStore,
                stateStore,
                subagentToolFactory,
                harnessToolkitFactory,
                mcpServerService,
                harnessObservability);
    }

    @Test
    void trimsTheLastTurnForRegeneration() {
        var state = new AgentState();
        state.addMessage(UserMessage.from("first"));
        state.addMessage(AiMessage.from("first answer"));
        state.addMessage(UserMessage.from("second"));
        state.addMessage(AiMessage.from("second answer"));

        AiChatService.trimLastTurn(state);

        assertThat(state.messages()).hasSize(2);
        assertThat(state.messages().getLast()).isInstanceOf(AiMessage.class);
    }

    @Test
    void completeEnqueuesAnL1TaskForTheAssistantMessage() {
        stubPrepare(true);
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
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
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
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
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
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
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("队列不可用")));

        StepVerifier.create(service.complete(command()))
                .assertNext(result -> assertThat(result.messageId()).isEqualTo("21"))
                .verifyComplete();

        verify(memoryTaskScheduler).enqueueExtraction(any(), anyString(), anyLong());
    }

    @Test
    void expandsSlashCommandsBeforeCallingTheGateway() {
        stubPrepare(true);
        when(slashCommandService.resolve("agent-a", "/review 重点"))
                .thenReturn(Mono.just(new SlashCommandService.ResolvedSlashCommand("review", "评审这段内容")));
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong())).thenReturn(Mono.empty());

        var invocationCaptor = ArgumentCaptor.forClass(AiGatewayInvocation.class);
        var contentCaptor = ArgumentCaptor.forClass(String.class);

        StepVerifier.create(service.complete(commandWith("review", "重点")))
                .expectNextCount(1)
                .verifyComplete();

        verify(gatewayService).complete(invocationCaptor.capture());
        var last = invocationCaptor.getValue().ir().messages().getLast();
        assertThat(last.parts())
                .anySatisfy(part -> assertThat(((AiContentPart.Text) part).text()).isEqualTo("评审这段内容"));
        verify(conversationService)
                .appendUserMessage(any(), anyInt(), anyInt(), contentCaptor.capture(), anyString(), any());
        assertThat(contentCaptor.getValue()).isEqualTo("评审这段内容");
    }

    @Test
    void executesToolCallsAndRepeatsTheGatewayRound() {
        stubPrepare(true);
        when(gatewayService.complete(any()))
                .thenReturn(Mono.just(gatewayResultWithToolCall()))
                .thenReturn(Mono.just(gatewayResult()));
        when(toolService.execute(anyString(), any(), any())).thenReturn(Mono.just(toolOutcome()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong())).thenReturn(Mono.empty());
        var metadataCaptor = ArgumentCaptor.forClass(JsonNode.class);

        StepVerifier.create(service.complete(command()))
                .assertNext(result -> assertThat(result.content()).isEqualTo("先查一下\n\n回答"))
                .verifyComplete();

        verify(gatewayService, times(2)).complete(any());
        verify(conversationService).appendAssistantMessage(
                any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), metadataCaptor.capture());
        var metadata = metadataCaptor.getValue();
        assertThat(metadata.path("toolCalls").get(0).path("name").asText()).isEqualTo("skill");
        assertThat(metadata.path("toolResults").get(0).path("output").asText()).isEqualTo("技能内容");
    }

    @Test
    void streamEmitsToolCallAndToolResultBeforeTheFinalAnswer() {
        stubPrepare(true);
        when(gatewayService.stream(any()))
                .thenReturn(
                        Flux.just(
                                new AiGatewayEvent.Meta(gatewayMeta()),
                                new AiGatewayEvent.Delta(new AiStreamEvent(
                                        null,
                                        null,
                                        new AiStreamEvent.ToolCallDelta(0, "c1", "skill", "{\"name\":\"s1\"}"),
                                        null,
                                        null)),
                                new AiGatewayEvent.Done("", "tool_calls", false)),
                        Flux.just(
                                new AiGatewayEvent.Meta(gatewayMeta()),
                                new AiGatewayEvent.Delta(new AiStreamEvent("最终答案", null, null, null, null)),
                                new AiGatewayEvent.Done("21", "stop", false)));
        when(toolService.execute(anyString(), any(), any())).thenReturn(Mono.just(toolOutcome()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(service.stream(command()).collectList())
                .assertNext(events -> {
                    assertThat(events).anyMatch(AiChatStreamEvent.ToolCall.class::isInstance);
                    assertThat(events).anyMatch(AiChatStreamEvent.ToolResult.class::isInstance);
                    assertThat(events).anyMatch(AiChatStreamEvent.Done.class::isInstance);
                })
                .verifyComplete();

        verify(gatewayService, times(2)).stream(any());
    }

    @Test
    void streamEmitsHarnessTextDeltasAndPersistsTheCombinedAnswer() {
        stubPrepare(true);
        when(gatewayService.stream(any())).thenReturn(Flux.just(
                new AiGatewayEvent.Meta(gatewayMeta()),
                new AiGatewayEvent.Delta(new AiStreamEvent("你", null, null, null, null)),
                new AiGatewayEvent.Delta(new AiStreamEvent("好", null, null, null, null)),
                new AiGatewayEvent.Usage(new AiTokenUsage(1, 2, null, null), BigDecimal.ONE, "PROVIDER"),
                new AiGatewayEvent.Done("21", "stop", false)));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong())).thenReturn(Mono.empty());
        var contentCaptor = ArgumentCaptor.forClass(String.class);

        StepVerifier.create(service.stream(command()).collectList())
                .assertNext(events -> {
                    var deltas = events.stream()
                            .filter(AiChatStreamEvent.Delta.class::isInstance)
                            .map(AiChatStreamEvent.Delta.class::cast)
                            .toList();
                    assertThat(deltas).extracting(AiChatStreamEvent.Delta::content)
                            .containsExactly("你", "好");
                    assertThat(events).anyMatch(AiChatStreamEvent.Done.class::isInstance);
                })
                .verifyComplete();

        verify(conversationService).appendAssistantMessage(
                any(), anyInt(), anyInt(), contentCaptor.capture(), anyString(), anyString(), any(), any());
        assertThat(contentCaptor.getValue()).isEqualTo("你好");
    }

    @Test
    void regenerateDeletesTheLastAssistantMessageWithoutAppendingAUserMessage() {
        when(settingService.spec()).thenReturn(Mono.just(setting(true)));
        when(chatModeService.get("fast")).thenReturn(Mono.just(mode()));
        when(conversationService.requireOwned(7L, "user-1")).thenReturn(Mono.just(conversation()));
        when(teamService.requireAgent("agent-a", "user-1")).thenReturn(Mono.just(agent()));
        when(conversationService.deleteLastAssistant(any())).thenReturn(Mono.empty());
        when(conversationService.recentHistory(any(), anyInt())).thenReturn(Flux.just(userMessage()));
        when(memoryContextBuilder.build(any(), anyString(), anyInt(), anyInt())).thenReturn(Mono.empty());
        when(agentContextService.build(any())).thenReturn(Mono.just(new AiAgentContextService.AgentContext(
                "", List.of(), List.of(), List.of(), List.of(), Map.of())));
        when(hookService.dispatch(anyString(), any(AiHook.Event.class), any())).thenReturn(Mono.empty());
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        when(conversationService.appendAssistantMessage(
                        any(), anyInt(), anyInt(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(assistantMessage()));
        when(conversationService.markPending(any(), anyInt())).thenReturn(Mono.just(2));
        when(memoryTaskScheduler.enqueueExtraction(any(), anyString(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(service.complete(regenerateCommand()))
                .expectNextCount(1)
                .verifyComplete();

        verify(conversationService).deleteLastAssistant(any());
        verify(conversationService, never())
                .appendUserMessage(any(), anyInt(), anyInt(), anyString(), anyString(), any());
    }

    private void stubPrepare(boolean extractionEnabled) {
        when(settingService.spec()).thenReturn(Mono.just(setting(extractionEnabled)));
        when(chatModeService.get("fast")).thenReturn(Mono.just(mode()));
        when(teamService.requireAgent("default", "user-1")).thenReturn(Mono.just(agent()));
        when(conversationService.createOrGet(any())).thenReturn(Mono.just(conversation()));
        when(memoryContextBuilder.build(any(), anyString(), anyInt(), anyInt())).thenReturn(Mono.empty());
        when(conversationService.appendUserMessage(
                        any(), anyInt(), anyInt(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(userMessage()));
        when(conversationService.markPending(any(), anyInt())).thenReturn(Mono.just(2));
        when(agentContextService.build(any())).thenReturn(Mono.just(new AiAgentContextService.AgentContext(
                "", List.of(), List.of(), List.of(), List.of(), Map.of())));
        when(hookService.dispatch(anyString(), any(AiHook.Event.class), any())).thenReturn(Mono.empty());
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
                        null,
                        null,
                        null,
                        null,
                        false));
    }

    private static AiChatCommand commandWith(String command, String input) {
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
                        List.of(new AiChatCompletionRequest.Message("user", "原始内容")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        command,
                        input,
                        null,
                        false));
    }

    private static AiChatCommand regenerateCommand() {
        return new AiChatCommand(
                "user-1",
                Set.of("user"),
                null,
                new AiChatCompletionRequest(
                        7L,
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
                        null,
                        null,
                        null,
                        null,
                        true));
    }

    private static AiGatewayResult gatewayResultWithToolCall() {
        var arguments = cn.tolim.pudans.contract.infra.utils.JsonUtils.mapper()
                .createObjectNode()
                .put("name", "s1");
        return new AiGatewayResult(
                new AiChatResponse(
                        "resp-1",
                        "gpt-test",
                        new AiChatMessage(
                                "assistant",
                                List.of(
                                        new AiContentPart.Text("先查一下", null),
                                        new AiContentPart.ToolCall("c1", "skill", arguments, null))),
                        new AiTokenUsage(1, 2, null, null),
                        "tool_calls"),
                gatewayMeta(),
                new AiTokenUsage(1, 2, null, null),
                BigDecimal.ONE,
                "PROVIDER");
    }

    private static AiChatToolService.ToolOutcome toolOutcome() {
        return new AiChatToolService.ToolOutcome(
                "c1",
                "skill",
                null,
                cn.tolim.pudans.contract.infra.utils.JsonUtils.mapper()
                        .createObjectNode()
                        .put("name", "s1"),
                "技能内容",
                false,
                5L);
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
