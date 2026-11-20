import { axiosInstance } from "@pudans-dev/api-client";

import type { AiModelTier } from "../../providers/lib/api";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

type AiRoutingSummary = {
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

type AiRoutingDaily = {
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

export type AiRoutingHistoryQuery = {
  from?: string;
  to?: string;
  status?: string;
  provider?: string;
  model?: string;
  agent?: string;
  group?: string;
  beforeId?: number;
  limit?: number;
};

export type AiRoutingHistoryItem = {
  requestId: string;
  conversationId?: number;
  sessionKey?: string;
  sessionTitle?: string;
  agent: string;
  mode?: string;
  routeGroup?: string;
  provider?: string;
  account?: string;
  model?: string;
  tier?: AiModelTier;
  status: string;
  tries: number;
  latencyMs?: number;
  createdAt: string;
};

export type AiRouteTraceCandidate = {
  provider: string;
  account: string;
  model: string;
  tier?: AiModelTier;
  eligible: boolean;
  reason?: string;
  healthScore?: number;
  source?: string;
};

type AiRouteTraceClassification = {
  level?: string;
  confidence?: number;
  tier?: AiModelTier;
};

type AiRouteTraceMatchedRule = {
  name?: string;
  condition?: string;
  member?: string;
  priority?: number;
};

export type AiRouteTraceAttempt = {
  group?: string;
  provider: string;
  account: string;
  model: string;
  tier?: AiModelTier;
  source?: string;
  attempt: number;
  success: boolean;
  status?: number;
  errorCode?: string;
  latencyMillis?: number;
  at?: string;
};

type AiRouteTraceSelected = {
  provider?: string;
  account?: string;
  model?: string;
  tier?: AiModelTier;
};

export type AiRouteTrace = {
  routeMode?: string;
  requestedTier?: AiModelTier;
  resolvedTier?: AiModelTier;
  affinity?: string;
  classification?: AiRouteTraceClassification;
  candidates?: AiRouteTraceCandidate[];
  selected?: AiRouteTraceSelected;
  outcome?: string;
  fallback?: string[];
  matchedRule?: AiRouteTraceMatchedRule | null;
  attempts?: AiRouteTraceAttempt[];
  fallbackGroups?: string[];
  callerKey?: string | null;
};

export type AiRoutingHistoryDetail = AiRoutingHistoryItem & {
  completedAt?: string;
  routeTrace?: AiRouteTrace | null;
  retrievalTrace?: unknown;
};

export type AiRoutingHistoryPage = {
  items: AiRoutingHistoryItem[];
  nextCursor?: number | null;
};

export async function fetchRoutingHistory(
  query: AiRoutingHistoryQuery = {},
): Promise<AiRoutingHistoryPage> {
  const params: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "" && value !== 0) {
      params[key] = value;
    }
  }
  const response = await axiosInstance.get<AiRoutingHistoryPage>(
    `${API_PREFIX}/aistats/-/routing/history`,
    { params },
  );
  return response.data;
}

export async function fetchRoutingHistoryDetail(
  requestId: string,
): Promise<AiRoutingHistoryDetail> {
  const response = await axiosInstance.get<AiRoutingHistoryDetail>(
    `${API_PREFIX}/aistats/-/routing/history/${encodeURIComponent(requestId)}`,
  );
  return response.data;
}
