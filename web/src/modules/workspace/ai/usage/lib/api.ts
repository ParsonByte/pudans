import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { resolveApiError } from "@/lib/api-error";

import { AI_API_PREFIX } from "../../providers/lib/api";

const USAGE_API_PATH = `${AI_API_PREFIX}/aistats/-/usage`;

const STATS_API_PATH = `${AI_API_PREFIX}/aistats/-`;

const USAGE_SESSION_EXPORT_PAGE_SIZE = 200;

const USAGE_REQUEST_SAMPLE_MAX_ROWS = 2000;

const USAGE_REQUEST_SAMPLE_PAGE_SIZE = 200;

const PLAN_API_PATH = `${AI_API_PREFIX}/aiplan`;

export const USAGE_SESSION_EXPORT_MAX_ROWS = 2000;

const USAGE_REQUEST_SAMPLE_LIMIT = USAGE_REQUEST_SAMPLE_MAX_ROWS;

export type UsageScope = "self" | "all";

export type UsageBucket = "hour" | "day" | "week";

export type UsageTotals = {
  requests: number;
  succeeded: number;
  failed: number;
  running: number;
  inputTokens: number;
  cacheTokens: number;
  outputTokens: number;
  totalTokens: number;
  units: number;
  latencyMs: number | null;
  ttftMs: number | null;
  tps: number | null;
  cacheHitRate?: number | null;
  retriedRequests?: number;
  canceledRequests?: number;
  unpricedRequests?: number;
  reasoningTokens?: number;
};

export type UsageDaily = {
  day: string;
  requests: number;
  succeeded: number;
  failed: number;
  inputTokens: number;
  cacheTokens: number;
  outputTokens: number;
  totalTokens: number;
  units: number;
};

export type UsageRankingEntry = {
  name: string;
  provider: string | null;
  requests: number;
  failed: number;
  inputTokens: number;
  cacheTokens: number;
  outputTokens: number;
  units: number;
};

export type UsageRankings = {
  agents: UsageRankingEntry[];
  models: UsageRankingEntry[];
  providers: UsageRankingEntry[];
  accounts: UsageRankingEntry[];
};

export type PlanUsageWindow = {
  limit: number;
  used: number;
  reserved: number;
  remaining: number;
  startsAt?: string | null;
  endsAt?: string | null;
  state?: string | null;
};

export type PlanVoucherBalance = {
  total: number;
  used: number;
  remaining: number;
};

type PlanRateLimit = {
  maxConcurrency?: number | null;
  maxRpm?: number | null;
};

export type PlanUsage = {
  plan?: {
    name?: string | null;
    displayName?: string | null;
    matchedBy?: string | null;
  } | null;
  fiveHour: PlanUsageWindow;
  weekly: PlanUsageWindow;
  rateLimit?: PlanRateLimit | null;
  vouchers?: {
    fiveHour?: PlanVoucherBalance | null;
    weekly?: PlanVoucherBalance | null;
  } | null;
};

export type PlanVoucherKind = "FIVE_HOUR_RESET" | "WEEKLY_RESET";

export type PlanVoucherUseRequest = {
  kind: PlanVoucherKind;
  requestId: string;
};

export type PlanVoucherUseResult = {
  requestId?: string;
  kind?: PlanVoucherKind | null;
  duplicate?: boolean;
  targetWindowKey?: string;
  windowKeyAfter?: string;
  usedAt?: string | null;
};

export type UsageOverview = {
  totals: UsageTotals;
  series: UsageDaily[];
  rankings: UsageRankings;
};

export type UsageOverviewQuery = {
  from: string;
  to: string;
  bucket?: UsageBucket;
  scope?: UsageScope;
};

export type UsageRequestQuery = {
  from: string;
  to: string;
  status?: string;
  provider?: string;
  model?: string;
  account?: string;
  agent?: string;
  mode?: string;
  routeGroup?: string;
  q?: string;
  purpose?: string;
  via?: string;
  callerKey?: string;
  retried?: string;
  scope?: UsageScope;
};

export type UsageRequestItem = {
  requestId: string;
  userId: string;
  teamId: string;
  agent: string;
  agentSource: string;
  mode: string | null;
  routeGroup: string | null;
  provider: string | null;
  account: string | null;
  model: string | null;
  tier: string | null;
  status: string;
  errorCode: string | null;
  inputTokens: number | null;
  cacheTokens: number | null;
  outputTokens: number | null;
  totalTokens: number | null;
  units: number | null;
  latencyMs: number | null;
  ttftMs: number | null;
  tps: number | null;
  sessionKey: string | null;
  conversationId: number | null;
  createdAt: string;
  completedAt: string | null;
  cacheHitRate?: number | null;
  cacheReadTokens?: number | null;
  cacheWriteTokens?: number | null;
  retried?: boolean | null;
  retryCount?: number | null;
  servedEffort?: string | null;
};

export type UsageRequestPage = {
  items: UsageRequestItem[];
  nextCursor: number | null;
  totals: UsageTotals;
};

type UsageBilling = {
  source: string | null;
  status: string;
  usageSource: string | null;
  upstreamRequestId: string | null;
};

export type UsageRequestDetail = UsageRequestItem & {
  billing: UsageBilling;
  routeTrace: unknown;
  retrievalTrace: unknown;
  vendor?: string | null;
  stream?: boolean | null;
  servedEffort?: string | null;
  cacheHitRate?: number | null;
  retried?: boolean | null;
};

export type UsageSessionQuery = {
  from: string;
  to: string;
  agent?: string;
  q?: string;
  provider?: string;
  model?: string;
  status?: string;
  routeGroup?: string;
  scope?: UsageScope;
};

export type UsageSession = {
  conversationId: number;
  title: string | null;
  userId: string;
  teamId: string;
  agent: string;
  agentSource: string;
  status: string;
  model: string | null;
  messageCount: number;
  requestCount: number;
  inputTokens: number;
  cacheTokens: number;
  outputTokens: number;
  totalTokens: number;
  units: number;
  durationSeconds: number;
  createdAt: string;
  lastActivityAt: string;
};

export type UsageSessionPage = {
  items: UsageSession[];
  nextCursor: number | null;
};

type UsageRecentMessage = {
  messageId: number;
  role: string;
  content: string;
  tokens: number | null;
  model: string | null;
  createdAt: string;
};

export type UsageSessionDetail = {
  conversation: UsageSession;
  messageCount: number;
  userMessageCount: number;
  assistantMessageCount: number;
  recentMessages: UsageRecentMessage[];
};

export type UsageSessionExportResult = {
  items: UsageSession[];
  truncated: boolean;
};

export type UsageContextQuery = {
  from: string;
  to: string;
  agent?: string;
  scope?: UsageScope;
};

export type UsageContextTotals = {
  requests: number;
  inputTokens: number;
  outputTokens: number;
  systemTokens: number;
  historyTokens: number;
  memoryTokens: number;
  kbTokens: number;
  userTokens: number;
  contextWindowTokens: number | null;
  windowUtilization: number | null;
};

export type UsageContextBreakdown = UsageContextTotals & {
  name: string;
  contextWindow: number | null;
};

export type UsageContext = {
  totals: UsageContextTotals;
  agents: UsageContextBreakdown[];
  models: UsageContextBreakdown[];
};

export type UsageHeatmapMetric = "requests" | "tokens" | "units";

export type UsageHeatmapBucket = {
  date: string;
  hour?: number;
  requests: number;
  tokens: number;
  units: number;
};

export type UsageHeatmap = {
  metric: UsageHeatmapMetric;
  start: string;
  end: string;
  buckets: UsageHeatmapBucket[];
};

export type UsageHeatmapQuery = {
  from: string;
  to: string;
  metric: UsageHeatmapMetric;
  scope?: UsageScope;
};

export type UsageSpeedGroupBy = "model" | "provider";

export type UsageSpeedItem = {
  name: string;
  provider?: string | null;
  requests: number;
  failures: number;
  avgTtftMs: number | null;
  p50TtftMs: number | null;
  avgTps: number | null;
  p50Tps: number | null;
};

export type UsageSpeed = {
  items: UsageSpeedItem[];
};

export type UsageSpeedQuery = {
  from: string;
  to: string;
  groupBy: UsageSpeedGroupBy;
  scope?: UsageScope;
};

type UsageSessionStatsTotals = {
  sessions: number;
  medianMessages: number;
  p90Messages: number;
  tokens: number;
  units: number;
  cacheReadTokens: number;
  activeSeconds: number;
};

export type UsageSessionStatsDay = {
  day: string;
  sessions: number;
  messages: number;
  tokens: number;
  units: number;
  activeSeconds: number;
};

export type UsageSessionStatsHour = {
  hour: number;
  sessions: number;
};

export type UsageSessionStatsModel = {
  name: string;
  sessions: number;
  tokens: number;
  units: number;
};

export type UsageSessionStatsTopSession = {
  conversationId: number;
  title: string | null;
  agent: string;
  model: string | null;
  messages: number;
  tokens: number;
  units: number;
  lastActivityAt: string;
};

type UsageSessionStatsShape = {
  bucket: string;
  messages: number;
};

export type UsageSessionStats = {
  totals: UsageSessionStatsTotals;
  series: UsageSessionStatsDay[];
  hours: UsageSessionStatsHour[];
  models: UsageSessionStatsModel[];
  topSessions: UsageSessionStatsTopSession[];
  shape: UsageSessionStatsShape[];
};

export type UsageSessionStatsQuery = {
  from: string;
  to: string;
  agent?: string;
  model?: string;
  provider?: string;
  status?: string;
  routeGroup?: string;
  scope?: UsageScope;
};

export type UsageContextPartKey = "system" | "history" | "memory" | "kb" | "user" | "output";

type UsageContextParts = Record<UsageContextPartKey, number>;

export type UsageContextGridItem = {
  requestId: string;
  agent: string;
  model: string | null;
  createdAt: string;
  inputTokens: number;
  outputTokens: number;
  contextWindow: number | null;
  utilization: number | null;
  parts: UsageContextParts;
};

export type UsageContextGrid = {
  items: UsageContextGridItem[];
};

export type UsageContextGridQuery = {
  from: string;
  to: string;
  agent?: string;
  model?: string;
  limit?: number;
  scope?: UsageScope;
};

export type UnitStatsRow = {
  day: string;
  model: string;
  requests: number;
  units: number;
  tokens: number;
};

export type UnitStats = {
  rows: UnitStatsRow[];
};

export type ModelStatsRow = {
  modelType: string;
  capability: string;
  model: string;
  status: string;
  requests: number;
  units: number;
  images: number;
  videoSeconds: number;
  audioSeconds: number;
  speechCharacters: number;
  tasks: number;
};

export type ModelStats = {
  rows: ModelStatsRow[];
};

export type MemoryStats = {
  l1Total: number;
  l1Active: number;
  l2Total: number;
  l3Total: number;
};

export type KnowledgeBaseStats = {
  documents: number;
  ready: number;
  failed: number;
  chunks: number;
  queries: number;
};

export type UsageClearRequest = {
  before: string;
  provider?: string;
  model?: string;
};

export type UsageClearResult = {
  requestsDeleted: number;
  modelRequestsDeleted: number;
};

function compactParams(
  params: Record<string, string | number | undefined>,
): Record<string, string | number> {
  const result: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      result[key] = value;
    }
  }
  return result;
}

export async function fetchUsageOverview(query: UsageOverviewQuery): Promise<UsageOverview> {
  const response = await axiosInstance.get<UsageOverview>(`${USAGE_API_PATH}/overview`, {
    params: compactParams({ ...query }),
  });
  return response.data;
}

export async function fetchUsageRequests(
  query: UsageRequestQuery,
  beforeId: number | undefined,
  limit: number,
): Promise<UsageRequestPage> {
  const response = await axiosInstance.get<UsageRequestPage>(`${USAGE_API_PATH}/requests`, {
    params: compactParams({ ...query, beforeId, limit }),
  });
  return response.data;
}

export async function fetchUsageRequestDetail(requestId: string): Promise<UsageRequestDetail> {
  const response = await axiosInstance.get<UsageRequestDetail>(
    `${USAGE_API_PATH}/requests/${encodeURIComponent(requestId)}`,
  );
  return response.data;
}

export async function fetchUsageRequestExport(query: UsageRequestQuery): Promise<Blob> {
  const response = await axiosInstance.get<Blob>(`${USAGE_API_PATH}/requests/-/export`, {
    params: compactParams({ ...query }),
    responseType: "blob",
  });
  return response.data;
}

export async function fetchUsageSessions(
  query: UsageSessionQuery,
  beforeId: number | undefined,
  limit: number,
): Promise<UsageSessionPage> {
  const response = await axiosInstance.get<UsageSessionPage>(`${USAGE_API_PATH}/sessions`, {
    params: compactParams({ ...query, beforeId, limit }),
  });
  return response.data;
}

export async function fetchUsageSessionDetail(conversationId: number): Promise<UsageSessionDetail> {
  const response = await axiosInstance.get<UsageSessionDetail>(
    `${USAGE_API_PATH}/sessions/${conversationId}`,
  );
  return response.data;
}

export async function fetchUsageSessionsForExport(
  query: UsageSessionQuery,
): Promise<UsageSessionExportResult> {
  const items: UsageSession[] = [];
  let cursor: number | undefined;
  while (items.length < USAGE_SESSION_EXPORT_MAX_ROWS) {
    const page = await fetchUsageSessions(query, cursor, USAGE_SESSION_EXPORT_PAGE_SIZE);
    items.push(...page.items);
    if (page.nextCursor == null || page.items.length === 0) {
      return { items: items.slice(0, USAGE_SESSION_EXPORT_MAX_ROWS), truncated: false };
    }
    if (items.length >= USAGE_SESSION_EXPORT_MAX_ROWS) {
      return { items: items.slice(0, USAGE_SESSION_EXPORT_MAX_ROWS), truncated: true };
    }
    cursor = page.nextCursor;
  }
  return { items: items.slice(0, USAGE_SESSION_EXPORT_MAX_ROWS), truncated: true };
}

export async function fetchUsageContext(query: UsageContextQuery): Promise<UsageContext> {
  const response = await axiosInstance.get<UsageContext>(`${USAGE_API_PATH}/context`, {
    params: compactParams({ ...query }),
  });
  return response.data;
}

export async function fetchUsageHeatmap(query: UsageHeatmapQuery): Promise<UsageHeatmap> {
  const response = await axiosInstance.get<UsageHeatmap>(`${USAGE_API_PATH}/heatmap`, {
    params: compactParams({ ...query }),
  });
  return response.data;
}

export async function fetchUsageSpeed(query: UsageSpeedQuery): Promise<UsageSpeed> {
  const response = await axiosInstance.get<UsageSpeed>(`${USAGE_API_PATH}/speed`, {
    params: compactParams({ ...query }),
  });
  return response.data;
}

export async function fetchUsageSessionsStats(
  query: UsageSessionStatsQuery,
): Promise<UsageSessionStats> {
  const response = await axiosInstance.get<UsageSessionStats>(`${USAGE_API_PATH}/sessions/stats`, {
    params: compactParams({ ...query }),
  });
  return response.data;
}

export async function fetchUsageContextGrid(
  query: UsageContextGridQuery,
): Promise<UsageContextGrid> {
  const response = await axiosInstance.get<UsageContextGrid>(`${USAGE_API_PATH}/context/grid`, {
    params: compactParams({ ...query }),
  });
  return response.data;
}

export async function fetchUsageRequestSample(
  query: UsageRequestQuery,
  maxRows = USAGE_REQUEST_SAMPLE_LIMIT,
): Promise<UsageRequestItem[]> {
  const items: UsageRequestItem[] = [];
  let cursor: number | undefined;
  while (items.length < maxRows) {
    const limit = Math.min(USAGE_REQUEST_SAMPLE_PAGE_SIZE, maxRows - items.length);
    const page = await fetchUsageRequests(query, cursor, limit);
    items.push(...page.items);
    if (page.nextCursor == null || page.items.length === 0) {
      break;
    }
    cursor = page.nextCursor;
  }
  return items.slice(0, maxRows);
}

export async function fetchUnitStats(days = 14): Promise<UnitStats> {
  const response = await axiosInstance.get<UnitStats>(`${STATS_API_PATH}/units`, {
    params: { days },
  });
  return response.data;
}

export async function fetchModelStats(days = 14): Promise<ModelStats> {
  const response = await axiosInstance.get<ModelStats>(`${STATS_API_PATH}/models`, {
    params: { days },
  });
  return response.data;
}

export async function fetchMemoryStats(): Promise<MemoryStats> {
  const response = await axiosInstance.get<MemoryStats>(`${STATS_API_PATH}/memory`);
  return response.data;
}

export async function fetchKnowledgeBaseStats(): Promise<KnowledgeBaseStats> {
  const response = await axiosInstance.get<KnowledgeBaseStats>(`${STATS_API_PATH}/kb`);
  return response.data;
}

export async function fetchPlanUsage(): Promise<PlanUsage> {
  const response = await axiosInstance.get<PlanUsage>(`${PLAN_API_PATH}/-/usage`);
  return response.data;
}

export async function redeemPlanVoucher(
  request: PlanVoucherUseRequest,
): Promise<PlanVoucherUseResult> {
  const response = await axiosInstance.post<PlanVoucherUseResult>(
    `${PLAN_API_PATH}/-/vouchers/use`,
    request,
  );
  return response.data;
}

const PLAN_VOUCHER_ERROR_MESSAGES: Record<string, string> = {
  "ai-plan-voucher-missing": "没有可用的重置券。",
  "ai-plan-reset-busy": "当前有进行中的请求，暂时无法重置。",
  "ai-plan-reset-not-needed": "目标窗口没有用量，无需重置。",
  "ai-plan-voucher-conflict": "本次重置请求与已提交的请求冲突，请刷新后重试。",
};

const PLAN_USAGE_ERROR_MESSAGES: Record<string, string> = {
  "ai-plan-missing": "当前账号未分配 AI 套餐。",
  "ai-plan-quota-exceeded": "套餐额度不足。",
  "ai-plan-model-denied": "当前套餐不允许该模型或路由组。",
};

export function resolvePlanUsageApiError(error: unknown): string {
  if (isAxiosError<{ code?: string; detail?: string }>(error)) {
    const code = error.response?.data?.code?.trim();
    const detail = error.response?.data?.detail?.trim();
    const known = code ? PLAN_USAGE_ERROR_MESSAGES[code] : undefined;
    const message = known ?? detail;
    if (message) {
      return code ? `${message}（${code}）` : message;
    }
  }
  return resolveUsageApiError(error);
}

export function resolvePlanVoucherApiError(error: unknown): string {
  if (isAxiosError<{ code?: string; detail?: string }>(error)) {
    const code = error.response?.data?.code?.trim();
    const detail = error.response?.data?.detail?.trim();
    const known = code ? PLAN_VOUCHER_ERROR_MESSAGES[code] : undefined;
    const message = known ?? detail;
    if (message) {
      return code ? `${message}（${code}）` : message;
    }
  }
  return resolveUsageApiError(error);
}

export async function clearUsage(request: UsageClearRequest): Promise<UsageClearResult> {
  const response = await axiosInstance.post<UsageClearResult>(`${USAGE_API_PATH}/-/clear`, request);
  return response.data;
}

export function resolveUsageApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "用量数据不存在或已被删除。",
    conflict: "当前数据状态已变化，请刷新后重试。",
  });
}
