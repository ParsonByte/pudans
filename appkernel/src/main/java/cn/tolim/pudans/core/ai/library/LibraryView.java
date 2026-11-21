package cn.tolim.pudans.core.ai.library;

import cn.tolim.pudans.core.ai.library.instruction.Instructions;
import cn.tolim.pudans.core.ai.library.mcp.McpClients;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.library.skill.Skills;
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

    private final McpServers mcpServerService;

    private final Skills skillService;

    private final Instructions instructionService;

    public Flux<McpServers.McpServerView> mcpServers(String agentName) {
        return mcpServerService.byAgent(agentName);
    }

    public Mono<McpServers.McpToolCatalog> mcpTools(String agentName) {
        return mcpServerService.toolCatalog(agentName);
    }

    public Mono<McpClients.McpToolCallResult> callMcpTool(
            String agentName, String exposedName, @Nullable JsonNode arguments) {
        return mcpServerService.callTool(agentName, exposedName, arguments);
    }

    public Flux<Skills.SkillSummary> skills(String agentName) {
        return skillService.skillsForAgent(agentName);
    }

    public Mono<String> instructions(String agentName) {
        return instructionService.systemSections(agentName);
    }

    public Mono<Snapshot> snapshot(String agentName) {
        return Mono.zip(
                        mcpTools(agentName)
                                .defaultIfEmpty(new McpServers.McpToolCatalog(List.of())),
                        skills(agentName).collectList(),
                        instructions(agentName))
                .map(tuple -> new Snapshot(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    public record Snapshot(
            McpServers.McpToolCatalog mcpTools,
            List<Skills.SkillSummary> skills,
            String instructions) {}
}