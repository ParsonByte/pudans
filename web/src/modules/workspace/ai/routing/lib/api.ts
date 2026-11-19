import { axiosInstance } from "@pudans-dev/api-client";

import type { AiModelTier } from "../../providers/lib/api";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

export type AiRoutingSummary = {
  requests: number;
  failed: number;
  running: number;
  inputTokens: number;
  cacheTokens: number;
  outputTokens: number;
  points: number;
  todayRequests: number;
  todayFailed: number;
  todayTokens: number;
  todayPoints: number;
};

export type AiRoutingDaily = {
  day: string;
  requests: number;
  failed: number;
  points: number;
};

export type AiRoutingRequest = {
  requestId: string;
  userId: string;
  agentSource: string;
  routeGroup?: string;
  provider?: string;
  account?: string;
  model?: string;
  tier?: AiModelTier;
  status: string;
  errorCode?: string;
  inputTokens?: number;
  cacheTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  points?: number;
  latencyMs?: number;
  ttftMs?: number;
  tps?: number;
  sessionKey?: string;
  conversationId?: number;
  createdAt: string;
  completedAt?: string;
};

export type AiRoutingAccountActivity = {
  provider: string;
  account: string;
  requests: number;
  success: number;
  failed: number;
};

export type AiRoutingUser = {
  userId: string;
  username?: string;
  displayName?: string;
  requests: number;
  failed: number;
  lastRequestAt?: string;
};

export type AiRoutingLive = {
  summary: AiRoutingSummary;
  daily: AiRoutingDaily[];
  requests: AiRoutingRequest[];
  accounts: AiRoutingAccountActivity[];
  users: AiRoutingUser[];
};

export async function fetchRoutingLive(days = 7, limit = 20): Promise<AiRoutingLive> {
  const response = await axiosInstance.get<AiRoutingLive>(`${API_PREFIX}/aistats/-/routing/live`, {
    params: { days, limit },
  });
  return response.data;
}
