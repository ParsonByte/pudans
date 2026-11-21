package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiAgent;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.core.ai.harness.permission.PermissionBehavior;
import cn.tolim.pudans.core.ai.harness.permission.PermissionMode;
import cn.tolim.pudans.core.ai.harness.skill.curator.InMemorySkillUsageStore;
import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageStore;
import cn.tolim.pudans.core.ai.harness.team.TeamRuntimeRegistry;
import cn.tolim.pudans.core.ai.library.LibraryView;
import cn.tolim.pudans.core.ai.library.command.SlashCommands;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariables;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.library.skill.Skills;
import cn.tolim.pudans.core.ai.library.subagent.Subagents;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentContextTest {

    @Mock
    LibraryView libraryView;

    @Mock
    Subagents subagents;

    @Mock
    SlashCommands slashCommands;

    @Mock
    EnvironmentVariables environmentVariables;

    @Mock
    Skills skills;

    @Mock
    TeamRuntimeRegistry teamRuntimeRegistry;

    private final SkillUsageStore skillUsageStore = new InMemorySkillUsageStore();

    private AgentContext service;

    @BeforeEach
    void setUp() {
        service = new AgentContext(
                libraryView,
                subagents,
                slashCommands,
                environmentVariables,
                skills,
                skillUsageStore,
                teamRuntimeRegistry);
    }

    @Test
    void mapsTheAgentHarnessSettings() {
        stubResources();
        var agent = agent();
        var harness = new AiAgent.Harness();
        harness.setPlanModeEnabled(true);
        harness.setTodosEnabled(false);
        harness.setCompactionTriggerMessages(12);
        harness.setCompactionKeepMessages(4);
        harness.setToolResultEvictionMaxChars(2048);
        harness.setConfirmationTimeoutSeconds(30);
        harness.setToolSearchEnabled(false);
        harness.setToolGroupsEnabled(false);
        harness.setAsyncToolPatterns(List.of("mcp.*.long_*"));
        harness.setExternalToolPatterns(List.of("mcp.*.dangerous"));
        harness.setWorkspaceEnabled(false);
        harness.setPermissionMode(AiAgent.Harness.PermissionMode.EXPLORE);
        var rule = new AiAgent.Harness.PermissionRule();
        rule.setToolName("echo");
        rule.setRuleContent("hello");
        rule.setBehavior(AiAgent.Harness.PermissionBehavior.ALLOW);
        harness.setPermissionRules(List.of(rule));
        agent.getSpec().setHarness(harness);

        StepVerifier.create(service.build(agent))
                .assertNext(context -> {
                    var settings = context.harness();
                    assertThat(settings.planModeEnabled()).isTrue();
                    assertThat(settings.todosEnabled()).isFalse();
                    assertThat(settings.compaction().enabled()).isTrue();
                    assertThat(settings.compaction().triggerMessages()).isEqualTo(12);
                    assertThat(settings.compaction().keepMessages()).isEqualTo(4);
                    assertThat(settings.eviction().enabled()).isTrue();
                    assertThat(settings.eviction().maxChars()).isEqualTo(2048);
                    assertThat(settings.confirmationTimeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(settings.toolSearchEnabled()).isFalse();
                    assertThat(settings.toolGroupsEnabled()).isFalse();
                    assertThat(settings.asyncToolPatterns()).containsExactly("mcp.*.long_*");
                    assertThat(settings.externalToolPatterns())
                            .containsExactly("mcp.*.dangerous");
                    assertThat(settings.workspaceEnabled()).isFalse();
                    assertThat(settings.permission().mode()).isEqualTo(PermissionMode.EXPLORE);
                    assertThat(settings.permission().rules()).singleElement().satisfies(config -> {
                        assertThat(config.toolName()).isEqualTo("echo");
                        assertThat(config.ruleContent()).isEqualTo("hello");
                        assertThat(config.behavior()).isEqualTo(PermissionBehavior.ALLOW);
                    });
                })
                .verifyComplete();
    }

    @Test
    void keepsHarnessDefaultsForAnAgentWithoutOverrides() {
        stubResources();
        var agent = agent();
        agent.getSpec().setHarness(null);

        StepVerifier.create(service.build(agent))
                .assertNext(context -> {
                    var settings = context.harness();
                    assertThat(settings.planModeEnabled()).isFalse();
                    assertThat(settings.todosEnabled()).isTrue();
                    assertThat(settings.compaction().enabled()).isTrue();
                    assertThat(settings.compaction().triggerMessages())
                            .isEqualTo(AiAgent.Harness.DEFAULT_COMPACTION_TRIGGER_MESSAGES);
                    assertThat(settings.eviction().enabled()).isTrue();
                    assertThat(settings.permission().mode()).isEqualTo(PermissionMode.BYPASS);
                })
                .verifyComplete();
    }

    private void stubResources() {
        when(libraryView.snapshot("agent-a"))
                .thenReturn(Mono.just(new LibraryView.Snapshot(
                        new McpServers.McpToolCatalog(List.of()), List.of(), "")));
        when(subagents.byAgent("agent-a")).thenReturn(Flux.empty());
        when(slashCommands.byAgent("agent-a")).thenReturn(Flux.empty());
        when(environmentVariables.resolveValues("agent-a")).thenReturn(Mono.just(Map.of()));
        when(skills.byAgent("agent-a")).thenReturn(Flux.empty());
    }

    private static AiAgent agent() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("agent-a");
        agent.setMetadata(metadata);
        return agent;
    }
}
