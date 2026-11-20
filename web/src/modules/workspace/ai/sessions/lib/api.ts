import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { resolveApiError } from "@/lib/api-error";
import { AI_API_PREFIX } from "@/modules/workspace/ai/providers/lib/api";
import {
  type ChatConversation,
  type ChatMessage,
  deleteChatConversation,
  fetchChatConversation,
} from "@/modules/workspace/index/lib/chat-api";

export type { ChatConversation, ChatMessage };

const CONVERSATIONS_API = `${AI_API_PREFIX}/conversations`;

const GATEWAY_SESSIONS_API = `${AI_API_PREFIX}/aigatewaysessions`;

export type AiGatewaySession = {
  id: number;
  spaceId: string;
  userId: string;
  agentSource: string;
  sessionKey: string;
  sessionId?: string | null;
  teamId: string;
  agentId: string;
  taskId?: string | null;
  conversationId?: number | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};

type CursorListParams = {
  beforeId?: number;
  limit: number;
};

type MessageListParams = {
  afterId?: number;
  limit: number;
};

export async function fetchConversations(params: CursorListParams): Promise<ChatConversation[]> {
  const response = await axiosInstance.get<ChatConversation[]>(CONVERSATIONS_API, { params });
  return response.data;
}

export async function fetchConversationMessages(
  conversationId: number,
  params: MessageListParams,
): Promise<ChatMessage[]> {
  const response = await axiosInstance.get<ChatMessage[]>(
    `${CONVERSATIONS_API}/${conversationId}/messages`,
    { params },
  );
  return response.data;
}

export async function fetchGatewaySessions(params: CursorListParams): Promise<AiGatewaySession[]> {
  const response = await axiosInstance.get<AiGatewaySession[]>(GATEWAY_SESSIONS_API, { params });
  return response.data;
}

export async function deleteGatewaySession(id: number): Promise<void> {
  await axiosInstance.delete(`${GATEWAY_SESSIONS_API}/${id}`);
}

export { deleteChatConversation as deleteConversation, fetchChatConversation as fetchConversation };

export function resolveAiSessionApiError(error: unknown): string {
  if (isAxiosError<{ detail?: string; errors?: string[] }>(error)) {
    const firstError = error.response?.data?.errors?.find((item) => item.trim());
    if (firstError) {
      return firstError;
    }
    const detail = error.response?.data?.detail;
    if (detail) {
      return detail;
    }
  }
  return resolveApiError(error, {
    notFound: "会话不存在或已被删除。",
    conflict: "会话状态已变更，请刷新后重试。",
  });
}
