import { axiosInstance } from "@pudans-dev/api-client";

import { resolveApiErrorWithDetail } from "@/lib/api-error";
import { type SseEvent, readSseEvents } from "@/lib/sse";
import { AI_AGENT_KIND, type AiAgentSpec } from "@/modules/workspace/ai/library/lib/api";
import {
  AI_API_PREFIX,
  AI_API_VERSION,
  type AiMetadata,
  type JsonPatchOperation,
  listAll,
} from "@/modules/workspace/ai/providers/lib/api";

export { AI_AGENT_KIND, AI_API_VERSION };
export type { JsonPatchOperation };

export const AI_SANDBOX_PROVIDER_KIND = "AiSandboxProvider";

export const AGENT_RUNTIME_AGENTS_QUERY_KEY = ["ai-agent-runtime-agents", "all"] as const;

export const AI_RUNTIMES_QUERY_KEY = ["ai-runtimes", "all"] as const;

export function aiRuntimeSnapshotsQueryKey(agent: string) {
  return ["ai-runtime-snapshots", agent] as const;
}

export const AI_SANDBOX_PROVIDERS_QUERY_KEY = ["ai-sandbox-providers", "all"] as const;

export const AI_RUNTIME_PROVIDERS_QUERY_KEY = ["ai-runtime-providers", "all"] as const;

export const AI_AGENT_RUNTIME_RELATED_QUERY_KEY = ["ai-agent-runtime-related", "all"] as const;

const RUNTIME_PAGE_SIZE = 100;

const RUNTIME_MAX_PAGES = 5;

export type AiRuntimeProvider = "NONE" | "ALIYUN" | "TENCENT";

export type SandboxState = "CREATING" | "RUNNING" | "STOPPING" | "STOPPED" | "FAILED" | "DESTROYED";

type AiAgentRuntimeConfig = {
  provider?: AiRuntimeProvider;
  image?: string;
  entrypoint?: string;
  cpu?: number;
  memoryMb?: number;
  timeoutSeconds?: number;
  autoStart?: boolean;
  networkEnabled?: boolean;
  workdir?: string;
};

export type AiRuntimeAgentSpec = AiAgentSpec & {
  prompt?: string;
  defaultRouteGroup?: string;
  defaultChatMode?: string;
  skills?: string[];
  mcpServers?: string[];
  hooks?: string[];
  environmentVariables?: string[];
  subagents?: string[];
  slashCommands?: string[];
  instructions?: string[];
  runtime?: AiAgentRuntimeConfig;
  metadata?: Record<string, unknown>;
};

export type AiRuntimeAgentItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiRuntimeAgentSpec;
};

export type AiRuntimeAgentCreatePayload = Omit<AiRuntimeAgentItem, "metadata"> & {
  metadata: { generateName: string };
};

export type AiRuntimeView = {
  agent: string;
  provider: Exclude<AiRuntimeProvider, "NONE">;
  state: SandboxState;
  sandboxId?: string | null;
  image?: string | null;
  entrypoint?: string | null;
  cpu?: number | null;
  memoryMb?: number | null;
  timeoutSeconds?: number | null;
  endpoint?: string | null;
  lastError?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  lastActiveAt?: string | null;
};

export type AiRuntimeSnapshotView = {
  id: number;
  agent: string;
  name: string;
  sizeBytes: number;
  createdBy?: string | null;
  createdAt: string;
};

export type AiRuntimeProviderInfo = {
  provider: string;
  displayName?: string | null;
  region?: string | null;
  endpoint?: string | null;
  defaultImage?: string | null;
  maxTimeoutSeconds: number;
  capabilities?: string[];
};

type SandboxProviderName = "ALIYUN" | "TENCENT";

export type SandboxProviderSpec = {
  provider: SandboxProviderName;
  displayName: string;
  endpoint?: string;
  region?: string;
  accessKeyId?: string;
  accessKeySecret?: string;
  defaultImage?: string;
  maxTimeoutSeconds?: number;
  enabled?: boolean;
};

export type SandboxProviderItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: SandboxProviderSpec;
};

export type SandboxProviderCreatePayload = Omit<SandboxProviderItem, "metadata"> & {
  metadata: { generateName: string };
};

export type AiRuntimeExecRequest = {
  command: string;
  args: string[];
  workdir?: string;
  timeoutSeconds?: number;
};

export type AiRuntimeExecResult = {
  exitCode: number;
  stdout: string;
  stderr: string;
  durationMs: number;
};

export type AiRuntimeFile = {
  path: string;
  encoding: string;
  content: string;
  size: number;
};

export type AiRuntimeFileWriteRequest = {
  path: string;
  encoding: string;
  content: string;
};

export type AiRuntimeLogEvent = {
  timestamp?: string | null;
  stream?: string | null;
  line: string;
};

export type RelatedResourceItem = {
  metadata: { name: string };
  spec?: {
    displayName?: string;
    name?: string;
    description?: string;
    enabled?: boolean;
  };
};

export type RelatedResources = {
  skills: RelatedResourceItem[];
  mcpServers: RelatedResourceItem[];
  hooks: RelatedResourceItem[];
  environmentVariables: RelatedResourceItem[];
  subagents: RelatedResourceItem[];
  slashCommands: RelatedResourceItem[];
  instructions: RelatedResourceItem[];
};

export type RelatedResourceFieldKey = keyof RelatedResources;

const RELATED_RESOURCE_PLURALS: Record<RelatedResourceFieldKey, string> = {
  skills: "aiskills",
  mcpServers: "aimcpservers",
  hooks: "aihooks",
  environmentVariables: "aienvironmentvariables",
  subagents: "aisubagents",
  slashCommands: "aislashcommands",
  instructions: "aiinstructions",
};

export function fetchRuntimeAgents(): Promise<AiRuntimeAgentItem[]> {
  return listAll<AiRuntimeAgentItem>("/aiagents");
}

export async function createRuntimeAgent(
  payload: AiRuntimeAgentCreatePayload,
): Promise<AiRuntimeAgentItem> {
  const response = await axiosInstance.post<AiRuntimeAgentItem>(
    `${AI_API_PREFIX}/aiagents`,
    payload,
  );
  return response.data;
}

export async function updateRuntimeAgent(agent: AiRuntimeAgentItem): Promise<AiRuntimeAgentItem> {
  const response = await axiosInstance.put<AiRuntimeAgentItem>(
    `${AI_API_PREFIX}/aiagents/${encodeURIComponent(agent.metadata.name)}`,
    agent,
  );
  return response.data;
}

export async function deleteRuntimeAgent(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/aiagents/${encodeURIComponent(name)}`);
}

type RuntimeListParams = {
  limit: number;
  beforeId?: number;
};

async function fetchRuntimes(params: RuntimeListParams): Promise<AiRuntimeView[]> {
  const response = await axiosInstance.get<AiRuntimeView[]>(`${AI_API_PREFIX}/airuntimes`, {
    params,
  });
  return response.data ?? [];
}

export async function fetchAllRuntimes(): Promise<AiRuntimeView[]> {
  const items: AiRuntimeView[] = [];
  let beforeId: number | undefined;
  for (let page = 0; page < RUNTIME_MAX_PAGES; page += 1) {
    const batch = await fetchRuntimes({ limit: RUNTIME_PAGE_SIZE, beforeId });
    items.push(...batch);
    if (batch.length < RUNTIME_PAGE_SIZE) {
      return items;
    }
    const lastUpdatedAt = batch[batch.length - 1]?.updatedAt;
    const nextBeforeId = lastUpdatedAt ? Date.parse(lastUpdatedAt) : Number.NaN;
    if (!Number.isFinite(nextBeforeId) || nextBeforeId === beforeId) {
      return items;
    }
    beforeId = nextBeforeId;
  }
  return items;
}

export async function fetchRuntime(agent: string): Promise<AiRuntimeView> {
  const response = await axiosInstance.get<AiRuntimeView>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}`,
  );
  return response.data;
}

export async function startRuntime(agent: string): Promise<AiRuntimeView> {
  const response = await axiosInstance.post<AiRuntimeView>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/start`,
  );
  return response.data;
}

export async function stopRuntime(agent: string): Promise<AiRuntimeView> {
  const response = await axiosInstance.post<AiRuntimeView>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/stop`,
  );
  return response.data;
}

export async function restartRuntime(agent: string): Promise<AiRuntimeView> {
  const response = await axiosInstance.post<AiRuntimeView>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/restart`,
  );
  return response.data;
}

export async function deleteRuntime(agent: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}`);
}

export async function fetchRuntimeSnapshots(agent: string): Promise<AiRuntimeSnapshotView[]> {
  const response = await axiosInstance.get<AiRuntimeSnapshotView[]>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/snapshots`,
  );
  return response.data ?? [];
}

export async function createRuntimeSnapshot(
  agent: string,
  name?: string,
): Promise<AiRuntimeSnapshotView> {
  const trimmed = name?.trim();
  const response = await axiosInstance.post<AiRuntimeSnapshotView>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/snapshots`,
    trimmed ? { name: trimmed } : {},
  );
  return response.data;
}

export async function restoreRuntimeSnapshot(agent: string, snapshotId: number): Promise<void> {
  await axiosInstance.post(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/snapshots/${encodeURIComponent(
      String(snapshotId),
    )}/restore`,
  );
}

export async function deleteRuntimeSnapshot(agent: string, snapshotId: number): Promise<void> {
  await axiosInstance.delete(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/snapshots/${encodeURIComponent(
      String(snapshotId),
    )}`,
  );
}

export async function execRuntimeCommand(
  agent: string,
  request: AiRuntimeExecRequest,
): Promise<AiRuntimeExecResult> {
  const response = await axiosInstance.post<AiRuntimeExecResult>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/exec`,
    request,
  );
  return response.data;
}

export async function readRuntimeFile(agent: string, path: string): Promise<AiRuntimeFile> {
  const response = await axiosInstance.get<AiRuntimeFile>(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/files`,
    { params: { path } },
  );
  return response.data;
}

export async function writeRuntimeFile(
  agent: string,
  request: AiRuntimeFileWriteRequest,
): Promise<void> {
  await axiosInstance.post(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/files`,
    request,
  );
}

export async function fetchRuntimeProviders(): Promise<AiRuntimeProviderInfo[]> {
  const response = await axiosInstance.get<AiRuntimeProviderInfo[]>(
    `${AI_API_PREFIX}/airuntimes/-/providers`,
  );
  return response.data ?? [];
}

export function fetchSandboxProviders(): Promise<SandboxProviderItem[]> {
  return listAll<SandboxProviderItem>("/aisandboxproviders");
}

export async function createSandboxProvider(
  payload: SandboxProviderCreatePayload,
): Promise<SandboxProviderItem> {
  const response = await axiosInstance.post<SandboxProviderItem>(
    `${AI_API_PREFIX}/aisandboxproviders`,
    payload,
  );
  return response.data;
}

export async function patchSandboxProvider(
  name: string,
  operations: JsonPatchOperation[],
): Promise<SandboxProviderItem> {
  const response = await axiosInstance.patch<SandboxProviderItem>(
    `${AI_API_PREFIX}/aisandboxproviders/${encodeURIComponent(name)}`,
    operations,
    { headers: { "Content-Type": "application/json-patch+json" } },
  );
  return response.data;
}

export async function deleteSandboxProvider(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/aisandboxproviders/${encodeURIComponent(name)}`);
}

export async function fetchRelatedResources(): Promise<RelatedResources> {
  const [skills, mcpServers, hooks, environmentVariables, subagents, slashCommands, instructions] =
    await Promise.all([
      listAll<RelatedResourceItem>(`/${RELATED_RESOURCE_PLURALS.skills}`),
      listAll<RelatedResourceItem>(`/${RELATED_RESOURCE_PLURALS.mcpServers}`),
      listAll<RelatedResourceItem>(`/${RELATED_RESOURCE_PLURALS.hooks}`),
      listAll<RelatedResourceItem>(`/${RELATED_RESOURCE_PLURALS.environmentVariables}`),
      listAll<RelatedResourceItem>(`/${RELATED_RESOURCE_PLURALS.subagents}`),
      listAll<RelatedResourceItem>(`/${RELATED_RESOURCE_PLURALS.slashCommands}`),
      listAll<RelatedResourceItem>(`/${RELATED_RESOURCE_PLURALS.instructions}`),
    ]);
  return {
    skills,
    mcpServers,
    hooks,
    environmentVariables,
    subagents,
    slashCommands,
    instructions,
  };
}

type RuntimeLogStreamOptions = {
  follow: boolean;
  signal?: AbortSignal;
  onEvent: (event: AiRuntimeLogEvent) => void;
};

export async function streamRuntimeLogs(
  agent: string,
  options: RuntimeLogStreamOptions,
): Promise<void> {
  const params = new URLSearchParams({ follow: String(options.follow) });
  const response = await fetch(
    `${AI_API_PREFIX}/airuntimes/${encodeURIComponent(agent)}/logs?${params.toString()}`,
    {
      method: "GET",
      credentials: "include",
      headers: {
        Accept: "text/event-stream",
        "X-Requested-With": "XMLHttpRequest",
      },
      signal: options.signal,
    },
  );
  if (!response.ok) {
    throw new Error(await readStreamError(response));
  }
  if (!response.body) {
    throw new Error("日志流没有返回响应内容。");
  }
  for await (const event of readSseEvents(response.body)) {
    const log = parseLogEvent(event);
    if (log) {
      options.onEvent(log);
    }
  }
}

export function resolveAgentRuntimeError(error: unknown): string {
  return resolveApiErrorWithDetail(error, {
    notFound: "运行时或资源不存在，可能已被销毁。",
    conflict: "操作冲突，请稍后重试。",
  });
}

function parseLogEvent(event: SseEvent): AiRuntimeLogEvent | null {
  if (event.event !== "log" || !event.data) {
    return null;
  }
  try {
    return JSON.parse(event.data) as AiRuntimeLogEvent;
  } catch {
    return null;
  }
}

async function readStreamError(response: Response): Promise<string> {
  let payload: { detail?: string; errors?: string[] } | null;
  try {
    payload = (await response.json()) as { detail?: string; errors?: string[] };
  } catch {
    payload = null;
  }
  const detail = payload?.errors?.find((item) => item.trim()) ?? payload?.detail;
  if (detail) {
    return detail;
  }
  if (response.status === 401) {
    return "登录状态已失效，请重新登录。";
  }
  if (response.status === 403) {
    return "没有权限查看该运行时的日志。";
  }
  if (response.status === 404) {
    return "运行时不存在，请先启动运行时。";
  }
  return "日志流连接失败，请稍后重试。";
}
