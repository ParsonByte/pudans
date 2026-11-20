import type { McpHealth, McpServerItem, McpTransport } from "./api";

export const MCP_TRANSPORT_LABELS: Record<McpTransport, string> = {
  STDIO: "STDIO",
  HTTP: "HTTP",
  SSE: "SSE",
};

export const MCP_HEALTH_LABELS: Record<McpHealth, string> = {
  UNKNOWN: "未探测",
  HEALTHY: "健康",
  UNHEALTHY: "异常",
};

export const MCP_TRANSPORT_HINTS: Record<McpTransport, string> = {
  STDIO: "通过本地命令启动 MCP 进程。",
  HTTP: "通过 Streamable HTTP 连接远程 MCP 服务。",
  SSE: "通过 Server-Sent Events 连接远程 MCP 服务。",
};

export function mcpDisplayName(server: McpServerItem): string {
  return server.spec.displayName || server.metadata.name;
}

export function mcpEndpointLabel(server: McpServerItem): string {
  if (server.spec.transport === "STDIO") {
    return [server.spec.command, ...(server.spec.args ?? [])].filter(Boolean).join(" ");
  }
  return server.spec.url || "";
}

export function mcpHealthVariant(
  health: McpHealth | undefined,
): "secondary" | "destructive" | "outline" {
  if (health === "HEALTHY") {
    return "secondary";
  }
  if (health === "UNHEALTHY") {
    return "destructive";
  }
  return "outline";
}
