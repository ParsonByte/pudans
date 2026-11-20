package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiHook;
import cn.tolim.pudans.contract.core.extension.ai.AiSubagent;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayInvocation;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceManager;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceSpec;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceTools;
import cn.tolim.pudans.core.ai.library.LibraryView;
import cn.tolim.pudans.core.ai.library.hook.HookService;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import cn.tolim.pudans.core.ai.library.subagent.SubagentService;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.MemoryTools;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.endpoint.AiMemoryViewService;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimeService;
import cn.tolim.pudans.core.ai.runtime.RuntimeTools;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxFile;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxState;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AiChatToolServiceSubagentTest {

    private final LibraryView libraryView = mock(LibraryView.class);

    private final SkillService skillService = mock(SkillService.class);

    private final SubagentService subagentService = mock(SubagentService.class);

    private final HookService hookService = mock(HookService.class);

    private final AiGatewayService gatewayService = mock(AiGatewayService.class);

    private final AgentRuntimeService runtimeService = mock(AgentRuntimeService.class);

    private final cn.tolim.pudans.core.ai.harness.workspace.WorkspaceService workspaceService =
            mock(cn.tolim.pudans.core.ai.harness.workspace.WorkspaceService.class);

    private final AiMemoryViewService memoryViewService = mock(AiMemoryViewService.class);

    private final cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository memoryRepository =
            mock(cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository.class);

    private final AiChatToolService service = new AiChatToolService(
            libraryView,
            skillService,
            subagentService,
            hookService,
            gatewayService,
            runtimeService,
            workspaceService,
            memoryViewService,
            memoryRepository);

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tempDir;

    @Test
    void readsAndWritesWorkspaceFilesThroughTools() {
        when(hookService.dispatch(any(), any(AiHook.Event.class), any())).thenReturn(Mono.empty());
        var manager = new WorkspaceManager(
                WorkspaceSpec.defaults(tempDir.resolve("workspace")));
        when(workspaceService.manager("agent-a", "user-1")).thenReturn(manager);

        var write = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall(
                                "call-1",
                                WorkspaceTools.WRITE,
                                JsonUtils.mapper().createObjectNode()
                                        .put("path", "notes/a.md")
                                        .put("content", "hello"),
                                null))
                .block();
        var read = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall(
                                "call-2",
                                WorkspaceTools.READ,
                                JsonUtils.mapper().createObjectNode().put("path", "notes/a.md"),
                                null))
                .block();

        assertThat(write).isNotNull();
        assertThat(write.isError()).isFalse();
        assertThat(read).isNotNull();
        assertThat(read.isError()).isFalse();
        assertThat(read.output()).contains("hello");
    }

    @Test
    void readsWritesAndReportsRuntimeFilesThroughTools() {
        when(hookService.dispatch(any(), any(AiHook.Event.class), any())).thenReturn(Mono.empty());
        when(runtimeService.readFile("agent-a", "user-1", "/tmp/a"))
                .thenReturn(Mono.just(new SandboxFile("/tmp/a", "utf-8", "hi", 2)));
        when(runtimeService.writeFile("agent-a", "user-1", "/tmp/a", "utf-8", "next"))
                .thenReturn(Mono.empty());
        when(runtimeService.status("agent-a", "user-1"))
                .thenReturn(Mono.just(new SandboxStatus(SandboxState.RUNNING, "ok")));

        var read = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall(
                                "call-1",
                                RuntimeTools.READ,
                                JsonUtils.mapper().createObjectNode().put("path", "/tmp/a"),
                                null))
                .block();
        var write = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall(
                                "call-2",
                                RuntimeTools.WRITE,
                                JsonUtils.mapper().createObjectNode()
                                        .put("path", "/tmp/a")
                                        .put("content", "next")
                                        .put("encoding", "utf-8"),
                                null))
                .block();
        var status = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall(
                                "call-3",
                                RuntimeTools.STATUS,
                                JsonUtils.mapper().createObjectNode(),
                                null))
                .block();

        assertThat(read).isNotNull();
        assertThat(read.output()).contains("hi");
        assertThat(write).isNotNull();
        assertThat(write.isError()).isFalse();
        assertThat(status).isNotNull();
        assertThat(status.output()).contains("RUNNING").contains("ok");
    }

    @Test
    void searchesAndAddsLongTermMemory() {
        when(hookService.dispatch(any(), any(AiHook.Event.class), any())).thenReturn(Mono.empty());
        when(memoryViewService.search("user-1", "team-a", "agent-a", "部署", 5))
                .thenReturn(Mono.just(List.of(new MemoryInjection.Ref(
                        1L, "record-1", "生产环境部署在东京", MemoryType.WORK_FACT, "部署", 0.9))));
        var search = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall(
                                "call-1",
                                MemoryTools.SEARCH,
                                JsonUtils.mapper().createObjectNode().put("query", "部署"),
                                null))
                .block();

        assertThat(search).isNotNull();
        assertThat(search.output()).contains("生产环境部署在东京");

        when(memoryRepository.insert(any())).thenAnswer(invocation -> {
            var record = invocation.getArgument(0, cn.tolim.pudans.core.ai.memory.L1MemoryRecord.class);
            return Mono.just(record.withId(9L));
        });
        var add = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall(
                                "call-2",
                                MemoryTools.ADD,
                                JsonUtils.mapper().createObjectNode().put("content", "记住东京"),
                                null))
                .block();

        assertThat(add).isNotNull();
        assertThat(add.output()).contains("\"id\":9");
    }

    @Test
    void runsSubagentsThroughHarnessAgent() {
        var captured = new AtomicReference<AiGatewayInvocation>();
        when(hookService.dispatch(any(), any(AiHook.Event.class), any())).thenReturn(Mono.empty());
        when(subagentService.resolve("agent-a", "helper")).thenReturn(Mono.just(subagent()));
        when(gatewayService.complete(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.just(result());
        });
        var arguments = JsonUtils.mapper().createObjectNode()
                .put("name", "helper")
                .put("prompt", "summarize");

        var outcome = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall("call-1", "subagent", arguments, null))
                .block();

        assertThat(outcome).isNotNull();
        assertThat(outcome.output()).isEqualTo("sub-answer");
        assertThat(outcome.isError()).isFalse();
        var invocation = captured.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.userId()).isEqualTo("user-1");
        assertThat(invocation.agentSource()).isEqualTo("pudans-subagent");
        assertThat(invocation.agentName()).isEqualTo("agent-a");
        assertThat(invocation.routeGroupCode()).isEqualTo("fast");
        assertThat(invocation.modelOverride()).isEqualTo("gpt-x");
    }

    private static AiAgent agent() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("agent-a");
        agent.setMetadata(metadata);
        agent.getSpec().setTeam("team-a");
        return agent;
    }

    private static AiSubagent subagent() {
        var subagent = new AiSubagent();
        subagent.getSpec().setPrompt("You are a helper.");
        subagent.getSpec().setModel("gpt-x");
        subagent.getSpec().setRouteGroup("fast");
        return subagent;
    }

    private static AiGatewayResult result() {
        var response = new AiChatResponse(
                "resp-1",
                "gpt-x",
                new AiChatMessage(
                        "assistant", List.of(new AiContentPart.Text("sub-answer", null))),
                new AiTokenUsage(1, 2, null, null),
                "stop");
        return new AiGatewayResult(
                response,
                new AiGatewayMeta(
                        "sub-1", 1, "chat", "fast", "openai", "account-1", "gpt-x",
                        null, "team-1", "agent-a", "session-1"),
                new AiTokenUsage(1, 2, null, null),
                BigDecimal.ONE,
                "wallet");
    }
}