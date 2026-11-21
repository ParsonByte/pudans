package cn.tolim.pudans.core.ai.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiMcpServer;
import cn.tolim.pudans.core.ai.library.instruction.Instructions;
import cn.tolim.pudans.core.ai.library.mcp.McpClients;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.library.skill.Skills;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LibraryViewTest {

    @Mock
    McpServers mcpServerService;

    @Mock
    Skills skillService;

    @Mock
    Instructions instructionService;

    LibraryView view;

    @BeforeEach
    void setUp() {
        view = new LibraryView(mcpServerService, skillService, instructionService);
    }

    @Test
    void composesMcpSkillsAndInstructionsIntoASnapshot() {
        var catalog = new McpServers.McpToolCatalog(List.of(
                new McpServers.McpToolDefinition("mcp.a.search", "a", "search", null, null)));
        var summary = new Skills.SkillSummary("skill-a", "Skill A", "描述", "内容");
        when(mcpServerService.toolCatalog("agent-a")).thenReturn(Mono.just(catalog));
        when(skillService.skillsForAgent("agent-a")).thenReturn(Flux.just(summary));
        when(instructionService.systemSections("agent-a")).thenReturn(Mono.just("规则"));

        StepVerifier.create(view.snapshot("agent-a"))
                .assertNext(snapshot -> {
                    assertThat(snapshot.mcpTools().tools()).hasSize(1);
                    assertThat(snapshot.skills()).containsExactly(summary);
                    assertThat(snapshot.instructions()).isEqualTo("规则");
                })
                .verifyComplete();
    }

    @Test
    void exposesMcpServerViewsAndCalls() {
        var serverView = new McpServers.McpServerView(
                "a", "A", null, AiMcpServer.Transport.HTTP, true,
                AiMcpServer.Health.HEALTHY, 0, List.of(), null);
        var call = new McpClients.McpToolCallResult("ok", false);
        when(mcpServerService.byAgent("agent-a")).thenReturn(Flux.just(serverView));
        when(mcpServerService.callTool("agent-a", "mcp.a.search", null)).thenReturn(Mono.just(call));

        StepVerifier.create(view.mcpServers("agent-a"))
                .assertNext(found -> assertThat(found.name()).isEqualTo("a"))
                .verifyComplete();
        StepVerifier.create(view.callMcpTool("agent-a", "mcp.a.search", null))
                .assertNext(result -> assertThat(result.output()).isEqualTo("ok"))
                .verifyComplete();
    }
}