package cn.tolim.pudans.core.ai.harness.permission;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

class PermissionEngineTest {

    @Test
    void defaultsToAskingForAnUnknownTool() {
        var engine = new PermissionEngine(PermissionContext.builder().build());

        var decision = engine.check(request("echo"));

        assertThat(decision.behavior()).isEqualTo(PermissionBehavior.ASK);
    }

    @Test
    void appliesAllowDenyAndAskRulesInOrder() {
        var engine = new PermissionEngine(PermissionContext.builder()
                .allow("read")
                .ask("write")
                .deny("delete")
                .build());

        assertThat(engine.check(request("read")).behavior()).isEqualTo(PermissionBehavior.ALLOW);
        assertThat(engine.check(request("write")).behavior()).isEqualTo(PermissionBehavior.ASK);
        assertThat(engine.check(request("delete")).behavior()).isEqualTo(PermissionBehavior.DENY);
    }

    @Test
    void deniesWritesInExploreMode() {
        var engine = new PermissionEngine(PermissionContext.builder()
                .mode(PermissionMode.EXPLORE)
                .build());

        assertThat(engine.check(request("write_file")).behavior()).isEqualTo(PermissionBehavior.DENY);
        assertThat(engine.check(request("read_file")).behavior()).isEqualTo(PermissionBehavior.ASK);
    }

    @Test
    void bypassAndDontAskModesResolveTheFallbackDecision() {
        var bypass = new PermissionEngine(PermissionContext.bypass());
        var dontAsk = new PermissionEngine(PermissionContext.builder()
                .mode(PermissionMode.DONT_ASK)
                .build());

        assertThat(bypass.check(request("echo")).behavior()).isEqualTo(PermissionBehavior.ALLOW);
        assertThat(dontAsk.check(request("echo")).behavior()).isEqualTo(PermissionBehavior.DENY);
    }

    private static ToolExecutionRequest request(String name) {
        return ToolExecutionRequest.builder()
                .id("call-1")
                .name(name)
                .arguments("{}")
                .build();
    }
}