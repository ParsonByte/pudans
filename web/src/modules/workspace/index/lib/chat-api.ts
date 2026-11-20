import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { getCookie } from "@/lib/cookies";

import {
  AI_API_PREFIX,
  type AiModelCapability,
  type AiModelItem,
  type AiRouteGroupItem,
  type AiRouteGroupParentMode,
} from "../../ai/providers/lib/api";
import { modelDisplayName } from "../../ai/providers/lib/model";
import { effectiveParentMode } from "../../ai/route-groups/lib/model";

const CHAT_API = `${AI_API_PREFIX}/conversations`;

const CHAT_COMPLETIONS = `${AI_API_PREFIX}/chats/-/completions/stream`;

const CHAT_TOOL_CONFIRMATIONS = `${AI_API_PREFIX}/chats/-/tool-confirmations`;

const CHAT_MODES = `${AI_API_PREFIX}/chats/-/modes`;

const CHAT_COMMANDS = `${AI_API_PREFIX}/aislashcommands/-/by-agent`;

const CHAT_GENERATIONS = `${AI_API_PREFIX}/aigenerations`;

const GENERATION_POLL_INTERVAL_MS = 2000;

const GENERATION_POLL_TIMEOUT_MS = 10 * 60 * 1000;

export type ChatConversation = {
  id: number;
  userId: string;
  teamId: string;
  agentId: string;
  agentSource: string;
  sessionKey: string;
  title?: string;
  modeCode?: string;
  routeGroup?: string;
  status: string;
  pendingCount: number;
  lastActivityAt: string;
  createdAt: string;
  updatedAt: string;
};

export type ChatMessageAttachment = {
  fileId: string;
  type?: string;
  name?: string;
  contentType?: string;
  size?: number;
};

export type ChatToolCall = {
  id: string;
  name: string;
  server?: string;
  arguments?: unknown;
};

export type ChatToolResult = {
  id: string;
  name: string;
  output: string;
  isError: boolean;
  durationMs: number;
};

export type ChatToolConfirm = {
  runId?: string;
  toolCallId?: string;
  name?: string;
  message?: string;
};

type ChatRunStarted = {
  runId?: string;
};

type ChatToolExternal = {
  runId?: string;
  toolCallId?: string;
  name?: string;
  arguments?: string;
};

type ChatToolExternalResult = {
  runId?: string;
  toolCallId?: string;
  output?: string;
  isError?: boolean;
};

export type ChatArtifact = {
  kind: string;
  fileId?: string;
  url?: string;
  name?: string;
  mimeType?: string;
  size?: number;
  progress?: number;
};

export type ChatStreamStage = {
  stage?: string;
  detail?: string;
};

type ChatMessageMetadata = {
  attachments?: ChatMessageAttachment[];
  reasoning?: string;
  toolCalls?: ChatToolCall[];
  toolResults?: ChatToolResult[];
  artifact?: ChatArtifact;
};

export type ChatMessage = {
  id: number;
  conversationId: number;
  turnIndex: number;
  messageIndex: number;
  role: string;
  content?: string;
  contentType: string;
  tokenCount?: number;
  modelCode?: string;
  metadata?: ChatMessageMetadata;
  createdAt: string;
};

export type ChatAttachmentRef = {
  fileId: string;
  name: string;
  contentType: string;
  size: number;
  type: string;
};

export type ChatMode = {
  name: string;
  displayName?: string;
  routeGroup?: string;
  defaultTier?: string;
  kbCodes: string[];
};

export type ChatSlashCommand = {
  name: string;
  description?: string;
  argumentHint?: string;
};

export type ChatAgent = {
  name: string;
  displayName: string;
};

export type ChatModel = {
  name: string;
  label: string;
};

export type ChatRouteGroup = {
  name: string;
  displayName: string;
  parentMode: AiRouteGroupParentMode;
  isDefault: boolean;
};

type ChatGenerationCapability = Extract<AiModelCapability, "IMAGE_GENERATION" | "VIDEO_GENERATION">;

export type ChatGenerationKind = "IMAGE" | "VIDEO" | "DOCUMENT";

export type ChatGenerationOutput = {
  type: string;
  url?: string;
  data?: string;
  mediaType?: string;
  fileId?: string;
  filename?: string;
};

export type ChatGeneration = {
  requestId: string;
  status: string;
  outputs?: ChatGenerationOutput[];
  errorMessage?: string;
};

export type ChatDocumentResult = {
  requestId: string;
  fileId: string;
  name: string;
  mimeType: string;
  size: number;
  modelCode?: string;
};

export type ChatArtifactInput = {
  kind: string;
  prompt: string;
  requestId?: string;
  fileId?: string;
  url?: string;
  name?: string;
  mimeType?: string;
  size?: number;
};

type ChatStreamMeta = {
  requestId?: string;
  conversationId?: number;
  mode?: string;
  routeGroup?: string;
  provider?: string;
  account?: string;
  model?: string;
  tier?: string;
  teamId?: string;
  agentId?: string;
  sessionKey?: string;
};

type ChatStreamUsage = {
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number;
  cacheWriteTokens?: number;
  units?: number;
  billingSource?: string;
};

export type ChatStreamDone = {
  messageId?: string;
  finishReason?: string;
  partial?: boolean;
};

export type ChatStreamError = {
  code?: string;
  message?: string;
  retryable?: boolean;
  partial?: boolean;
};

export type ChatStreamHandlers = {
  onMeta?: (meta: ChatStreamMeta) => void;
  onDelta?: (delta: { content?: string; reasoning?: string }) => void;
  onRunStarted?: (event: ChatRunStarted) => void;
  onToolCall?: (call: ChatToolCall) => void;
  onToolResult?: (result: ChatToolResult) => void;
  onToolConfirm?: (confirm: ChatToolConfirm) => void;
  onToolExternal?: (external: ChatToolExternal) => void;
  onToolExternalResult?: (result: ChatToolExternalResult) => void;
  onArtifact?: (artifact: ChatArtifact) => void;
  onStage?: (stage: ChatStreamStage) => void;
  onUsage?: (usage: ChatStreamUsage) => void;
  onDone?: (done: ChatStreamDone) => void;
  onError?: (error: ChatStreamError) => void;
};

export type ChatStreamDraft = {
  content: string;
  reasoning: string;
  toolCalls: ChatToolCall[];
  toolResults: ChatToolResult[];
  artifacts: ChatArtifact[];
  stage: ChatStreamStage | null;
};

export type ChatRequest = {
  conversationId?: number;
  mode?: string;
  routeGroup?: string;
  agentId?: string;
  kbCodes?: string[];
  messages: { role: string; content: string }[];
  attachments?: {
    storageFileId: string;
    type: string;
    name?: string;
    contentType?: string;
    size?: number;
  }[];
  command?: string;
  commandInput?: string;
  model?: string;
  regenerate?: boolean;
  idempotencyKey?: string;
};

export async function fetchChatModes(): Promise<ChatMode[]> {
  const response = await axiosInstance.get<ChatMode[]>(CHAT_MODES);
  return response.data;
}

type SlashCommandResource = {
  metadata: { name: string };
  spec?: {
    name?: string;
    description?: string;
    argumentHint?: string;
    enabled?: boolean;
  };
};

export async function fetchChatCommands(agent: string): Promise<ChatSlashCommand[]> {
  const response = await axiosInstance.get<SlashCommandResource[]>(CHAT_COMMANDS, {
    params: { agent },
  });
  return (response.data ?? [])
    .filter((command) => command.spec?.enabled !== false && command.spec?.name)
    .map((command) => ({
      name: command.spec?.name ?? command.metadata.name,
      description: command.spec?.description,
      argumentHint: command.spec?.argumentHint,
    }));
}

export function toChatModels(models: AiModelItem[]): ChatModel[] {
  return models
    .filter((model) => model.spec.modelType === "CHAT" && model.spec.enabled !== false)
    .map((model) => ({
      name: model.metadata.name,
      label: modelDisplayName(model),
    }));
}

export function toChatRouteGroups(groups: AiRouteGroupItem[]): ChatRouteGroup[] {
  return groups
    .filter((group) => group.spec.enabled !== false)
    .map((group) => ({
      name: group.metadata.name,
      displayName: group.spec.displayName || group.metadata.name,
      parentMode: effectiveParentMode(group),
      isDefault: Boolean(group.spec.isDefault),
    }))
    .sort((left, right) => {
      const rank = { FAST: 0, PRO: 1, NONE: 2 } as const;
      return rank[left.parentMode] - rank[right.parentMode] || left.name.localeCompare(right.name);
    });
}

export async function branchConversation(
  conversationId: number,
  messageId: number,
): Promise<ChatConversation> {
  const response = await axiosInstance.post<ChatConversation>(
    `${CHAT_API}/${conversationId}/branch`,
    { messageId },
  );
  return response.data;
}

export async function appendConversationArtifact(
  conversationId: number,
  artifact: ChatArtifactInput,
): Promise<ChatMessage> {
  const response = await axiosInstance.post<ChatMessage>(
    `${CHAT_API}/${conversationId}/artifacts`,
    artifact,
  );
  return response.data;
}

type GenerationRequest = {
  capability: ChatGenerationCapability;
  routeGroup?: string;
  inputs: { type: "text"; text: string }[];
  params: Record<string, unknown>;
  idempotencyKey: string;
};

async function createChatGeneration(request: GenerationRequest): Promise<ChatGeneration> {
  const response = await axiosInstance.post<ChatGeneration>(CHAT_GENERATIONS, request);
  return response.data;
}

export function generateImage(prompt: string, routeGroup?: string): Promise<ChatGeneration> {
  return createChatGeneration({
    capability: "IMAGE_GENERATION",
    routeGroup,
    inputs: [{ type: "text", text: prompt }],
    params: { size: "1024x1024", n: 1 },
    idempotencyKey: crypto.randomUUID(),
  });
}

export function generateVideo(prompt: string, routeGroup?: string): Promise<ChatGeneration> {
  return createChatGeneration({
    capability: "VIDEO_GENERATION",
    routeGroup,
    inputs: [{ type: "text", text: prompt }],
    params: { durationSeconds: 5 },
    idempotencyKey: crypto.randomUUID(),
  });
}

export async function generateDocument(input: {
  title: string;
  prompt: string;
  format: "MARKDOWN" | "HTML";
  routeGroup?: string;
}): Promise<ChatDocumentResult> {
  const response = await axiosInstance.post<ChatDocumentResult>(
    `${CHAT_GENERATIONS}/-/documents`,
    input,
  );
  return response.data;
}

async function fetchGeneration(requestId: string): Promise<ChatGeneration> {
  const response = await axiosInstance.get<ChatGeneration>(
    `${CHAT_GENERATIONS}/${encodeURIComponent(requestId)}`,
  );
  return response.data;
}

export async function waitForChatGeneration(
  requestId: string,
  onStatus?: (generation: ChatGeneration) => void,
  signal?: AbortSignal,
): Promise<ChatGeneration> {
  const deadline = Date.now() + GENERATION_POLL_TIMEOUT_MS;
  let generation = await fetchGeneration(requestId);
  onStatus?.(generation);
  while (generation.status === "PENDING" || generation.status === "RUNNING") {
    if (Date.now() >= deadline) {
      throw new Error("生成超时，请稍后重试。");
    }
    await new Promise((resolve) => setTimeout(resolve, GENERATION_POLL_INTERVAL_MS));
    signal?.throwIfAborted();
    generation = await fetchGeneration(requestId);
    onStatus?.(generation);
  }
  return generation;
}

export async function uploadChatAttachment(
  file: File,
  onUploadProgress?: (percent: number) => void,
): Promise<ChatAttachmentRef> {
  const form = new FormData();
  form.append("file", file, file.name);
  const response = await axiosInstance.post<ChatAttachmentRef>(
    `${AI_API_PREFIX}/chats/-/attachments`,
    form,
    {
      onUploadProgress: (event) => {
        if (event.total && onUploadProgress) {
          onUploadProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
        }
      },
    },
  );
  return response.data;
}

export function chatAttachmentUrl(fileId: string): string {
  return `${AI_API_PREFIX}/chats/-/attachments/${encodeURIComponent(fileId)}`;
}

export async function fetchChatConversations(limit = 50): Promise<ChatConversation[]> {
  const response = await axiosInstance.get<ChatConversation[]>(CHAT_API, {
    params: { limit },
  });
  return response.data;
}

export async function fetchChatConversation(id: number): Promise<ChatConversation> {
  const response = await axiosInstance.get<ChatConversation>(`${CHAT_API}/${id}`);
  return response.data;
}

export async function createChatConversation(input: {
  agentName?: string;
  title?: string;
  modeCode?: string;
  routeGroup?: string;
}): Promise<ChatConversation> {
  const response = await axiosInstance.post<ChatConversation>(CHAT_API, input);
  return response.data;
}

export async function deleteChatConversation(id: number): Promise<void> {
  await axiosInstance.delete(`${CHAT_API}/${id}`);
}

export async function fetchChatMessages(
  conversationId: number,
  limit = 200,
): Promise<ChatMessage[]> {
  const response = await axiosInstance.get<ChatMessage[]>(
    `${CHAT_API}/${conversationId}/messages`,
    { params: { limit } },
  );
  return response.data;
}

export async function confirmChatTool(
  runId: string,
  toolCallId: string,
  approved: boolean,
): Promise<void> {
  await axiosInstance.post(CHAT_TOOL_CONFIRMATIONS, { runId, toolCallId, approved });
}

export async function streamChat(
  request: ChatRequest,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const csrfToken = getCookie("XSRF-TOKEN");
  const response = await fetch(CHAT_COMPLETIONS, {
    method: "POST",
    credentials: "include",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
      ...(csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {}),
    },
    body: JSON.stringify(request),
    signal,
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  if (!response.body) {
    throw new Error("网关没有返回流式响应。");
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let boundary = findSseBoundary(buffer);
      while (boundary) {
        const block = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary.length);
        if (!dispatch(block, handlers)) {
          return;
        }
        boundary = findSseBoundary(buffer);
      }
    }
    if (buffer.trim()) {
      dispatch(buffer, handlers);
    }
  } finally {
    reader.releaseLock();
  }
}

export function resolveChatError(error: unknown): string {
  if (isAxiosError<{ detail?: string }>(error)) {
    return error.response?.data?.detail ?? "请求失败，请稍后重试。";
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return "请求失败，请稍后重试。";
}

function findSseBoundary(buffer: string): { index: number; length: number } | null {
  const lf = buffer.indexOf("\n\n");
  const crlf = buffer.indexOf("\r\n\r\n");
  if (lf < 0 && crlf < 0) {
    return null;
  }
  if (crlf >= 0 && (lf < 0 || crlf < lf)) {
    return { index: crlf, length: 4 };
  }
  return { index: lf, length: 2 };
}

function dispatch(block: string, handlers: ChatStreamHandlers): boolean {
  let event = "message";
  const data: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      data.push(line.slice(5).trimStart());
    }
  }
  if (event === "ping" || data.length === 0) {
    return true;
  }
  let payload: unknown;
  try {
    payload = JSON.parse(data.join("\n"));
  } catch {
    return true;
  }
  switch (event) {
    case "meta":
      handlers.onMeta?.(payload as ChatStreamMeta);
      break;
    case "run_started":
      handlers.onRunStarted?.(payload as ChatRunStarted);
      break;
    case "delta":
      handlers.onDelta?.(payload as { content?: string; reasoning?: string });
      break;
    case "tool_call":
      handlers.onToolCall?.(payload as ChatToolCall);
      break;
    case "tool_result":
      handlers.onToolResult?.(payload as ChatToolResult);
      break;
    case "tool_confirm":
      handlers.onToolConfirm?.(payload as ChatToolConfirm);
      break;
    case "tool_external":
      handlers.onToolExternal?.(payload as ChatToolExternal);
      break;
    case "tool_external_result":
      handlers.onToolExternalResult?.(payload as ChatToolExternalResult);
      break;
    case "artifact":
      handlers.onArtifact?.(payload as ChatArtifact);
      break;
    case "stage":
      handlers.onStage?.(payload as ChatStreamStage);
      break;
    case "usage":
      handlers.onUsage?.(payload as ChatStreamUsage);
      break;
    case "done":
      handlers.onDone?.(payload as ChatStreamDone);
      break;
    case "error":
      handlers.onError?.(payload as ChatStreamError);
      return false;
    default:
      break;
  }
  return true;
}

async function readError(response: Response): Promise<string> {
  try {
    const payload = (await response.json()) as { detail?: string; message?: string };
    return payload.detail ?? payload.message ?? `请求失败（${response.status}）。`;
  } catch {
    return `请求失败（${response.status}）。`;
  }
}
