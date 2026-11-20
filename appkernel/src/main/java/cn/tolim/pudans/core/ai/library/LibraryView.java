package cn.tolim.pudans.core.ai.library;

import cn.tolim.pudans.core.ai.library.instruction.InstructionService;
import cn.tolim.pudans.core.ai.library.mcp.McpClientService;
import cn.tolim.pudans.core.ai.library.mcp.McpServerService;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 聊天集成使用的资源库统一视图，避免聊天服务直接依赖各资源服务。
@Component
@RequiredArgsConstructor
public class LibraryView {

    private final McpServerService mcpServerService;

    private final SkillService skillService;

    private final InstructionService instructionService;

    public Flux<McpServerService.McpServerView> mcpServers(String agentName) {
        return mcpServerService.byAgent(agentName);
    }

    public Mono<McpServerService.McpToolCatalog> mcpTools(String agentName) {
        return mcpServerService.toolCatalog(agentName);
    }

    public Mono<McpClientService.McpToolCallResult> callMcpTool(
            String agentName, String exposedName, @Nullable JsonNode arguments) {
        return mcpServerService.callTool(agentName, exposedName, arguments);
    }

    public Flux<SkillService.SkillSummary> skills(String agentName) {
        return skillService.skillsForAgent(agentName);
    }

    public Mono<String> instructions(String agentName) {
        return instructionService.systemSections(agentName);
    }

    public Mono<Snapshot> snapshot(String agentName) {
        return Mono.zip(
                        mcpTools(agentName)
                                .defaultIfEmpty(new McpServerService.McpToolCatalog(List.of())),
                        skills(agentName).collectList(),
                        instructions(agentName))
                .map(tuple -> new Snapshot(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    public record Snapshot(
            McpServerService.McpToolCatalog mcpTools,
            List<SkillService.SkillSummary> skills,
            String instructions) {}
}