package cn.tolim.pudans.core.ai.library.mcp;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.removeFinalizers;

import cn.tolim.pudans.contract.ai.resource.AiMcpServer;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiMcpServerReconciler implements Reconciler<Reconciler.Request> {

    private static final String FINALIZER = "ai-mcp-server-lifecycle";

    private final ExtensionClient client;

    private final McpClients mcpClients;

    @Override
    public Result reconcile(Request request) {
        var optional = client.fetch(AiMcpServer.class, request.name());
        if (optional.isEmpty()) {
            return Result.doNotRetry();
        }
        var server = optional.get();
        if (isDeleted(server)) {
            mcpClients.close(request.name());
            removeFinalizers(server.getMetadata(), Set.of(FINALIZER));
            client.update(server);
            log.info("MCP 服务器 [{}] 已删除", request.name());
            return Result.doNotRetry();
        }
        if (addFinalizers(server.getMetadata(), Set.of(FINALIZER))) {
            client.update(server);
        }
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AiMcpServer()).build();
    }
}