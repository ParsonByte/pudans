import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { AI_API_PREFIX } from "../../ai/providers/lib/api";

const CHAT_API = `${AI_API_PREFIX}/conversations`;

const CHAT_COMPLETIONS = `${AI_API_PREFIX}/chats/-/completions/stream`;

const CHAT_MODES = `${AI_API_PREFIX}/chats/-/modes`;

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

export type ChatMessageMetadata = {
  attachments?: ChatMessageAttachment[];
  reasoning?: string;
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

export type ChatStreamMeta = {
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

export type ChatStreamUsage = {
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number;
  cacheWriteTokens?: number;
  points?: number;
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
  onUsage?: (usage: ChatStreamUsage) => void;
  onDone?: (done: ChatStreamDone) => void;
  onError?: (error: ChatStreamError) => void;
};

export type ChatRequest = {
  conversationId?: number;
  mode?: string;
  routeGroup?: string;
  messages: { role: string; content: string }[];
  attachments?: {
    storageFileId: string;
    type: string;
    name?: string;
    contentType?: string;
    size?: number;
  }[];
  idempotencyKey?: string;
};

export async function fetchChatModes(): Promise<ChatMode[]> {
  const response = await axiosInstance.get<ChatMode[]>(CHAT_MODES);
  return response.data;
}

export async function uploadChatAttachment(file: File): Promise<ChatAttachmentRef> {
  const form = new FormData();
  form.append("file", file, file.name);
  const response = await axiosInstance.post<ChatAttachmentRef>(
    `${AI_API_PREFIX}/chats/-/attachments`,
    form,
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

export async function streamChat(
  request: ChatRequest,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(CHAT_COMPLETIONS, {
    method: "POST",
    credentials: "include",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
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
  if (error instanceof Error && error.message) {
    return error.message;
  }
  if (isAxiosError<{ detail?: string }>(error)) {
    return error.response?.data?.detail ?? "请求失败，请稍后重试。";
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
    case "delta":
      handlers.onDelta?.(payload as { content?: string; reasoning?: string });
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
