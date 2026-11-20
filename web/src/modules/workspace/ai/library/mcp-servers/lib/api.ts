import { axiosInstance } from "@pudans-dev/api-client";

import {
  AI_API_PREFIX,
  AI_API_VERSION,
  type AiMetadata,
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  updateLibraryResource,
} from "../../lib/api";

export { AI_API_VERSION };

export const AI_MCP_SERVER_KIND = "AiMcpServer";

const MCP_SERVERS_PLURAL = "aimcpservers";

export const MCP_SERVERS_QUERY_KEY = ["ai-mcp-servers"] as const;

export const MCP_TRANSPORTS = ["STDIO", "HTTP", "SSE"] as const;

export type McpTransport = (typeof MCP_TRANSPORTS)[number];

export type McpHealth = "UNKNOWN" | "HEALTHY" | "UNHEALTHY";

export type McpServerSpec = {
  displayName: string;
  description?: string;
  transport: McpTransport;
  command?: string;
  args?: string[];
  url?: string;
  headers?: Record<string, string>;
  env?: Record<string, string>;
  toolAllowlist?: string[];
  enabled?: boolean;
  agents?: string[];
};

type McpServerStatus = {
  health?: McpHealth;
  toolCount?: number;
  lastCheckedAt?: string;
  message?: string;
};

export type McpServerItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: McpServerSpec;
  status?: McpServerStatus;
};

export type McpServerDraft = {
  apiVersion: string;
  kind: string;
  metadata: { generateName: string };
  spec: McpServerSpec;
};

type McpTool = {
  name: string;
  description?: string;
  inputSchema?: unknown;
};

export type McpTestResult = {
  healthy: boolean;
  toolCount: number;
  tools: McpTool[];
  message?: string;
};

export function fetchMcpServers(): Promise<McpServerItem[]> {
  return listLibraryResources<McpServerItem>(MCP_SERVERS_PLURAL);
}

export function createMcpServer(draft: McpServerDraft): Promise<McpServerItem> {
  return createLibraryResource<McpServerItem>(MCP_SERVERS_PLURAL, draft);
}

export function updateMcpServer(item: McpServerItem): Promise<McpServerItem> {
  return updateLibraryResource(MCP_SERVERS_PLURAL, item);
}

export function deleteMcpServer(name: string): Promise<void> {
  return deleteLibraryResource(MCP_SERVERS_PLURAL, name);
}

export async function testMcpServer(name: string): Promise<McpTestResult> {
  const response = await axiosInstance.post<McpTestResult>(
    `${AI_API_PREFIX}/${MCP_SERVERS_PLURAL}/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}
