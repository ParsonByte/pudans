import { apiClient, axiosInstance } from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";
import { beijingDateKey } from "@/lib/date";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

const ALERT_OPERATION = "RISK_ALERT";

const BLOCK_OPERATION = "RISK_BLOCKED";

type RiskRuleSpec = {
  scene: string;
  description?: string;
  enabled?: boolean;
  windowSeconds?: number;
  threshold?: number;
  blockSeconds?: number;
  metric?: string;
  action?: string;
};

export type RiskRuleItem = {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    version?: number;
    labels?: Record<string, string>;
    annotations?: Record<string, string>;
    creationTimestamp?: string;
  };
  spec: RiskRuleSpec;
};

type RiskRuleListResponse = {
  items?: RiskRuleItem[];
};

export type RiskEventItem = {
  auditId: string;
  operation: string;
  scene: string;
  subject: string;
  uid: string;
  username: string;
  path: string;
  requestMethod: string;
  traceId: string;
  result: string;
  count: number;
  blockSeconds: number;
  retryAfterMillis: number;
  timestamp: string;
};

export type RiskUnbanItem = {
  auditId: string;
  scene: string;
  subject: string;
  timestamp: string;
};

export type RiskBanItem = {
  scene: string;
  subject: string;
  expiresInSeconds: number;
};

export type RiskTrendPoint = {
  date: string;
  count: number;
};

export async function fetchRiskRules(): Promise<RiskRuleItem[]> {
  const response = await axiosInstance.get<RiskRuleListResponse>(`${API_PREFIX}/riskrules`, {
    params: { page: 1, size: 100 },
  });
  return response.data.items ?? [];
}

export async function updateRiskRule(rule: RiskRuleItem): Promise<void> {
  await axiosInstance.put(
    `${API_PREFIX}/riskrules/${encodeURIComponent(rule.metadata.name)}`,
    rule,
  );
}

export type RiskRuleCreateInput = {
  name: string;
  scene: string;
  description?: string;
  enabled: boolean;
  windowSeconds: number;
  threshold: number;
  blockSeconds: number;
  action: "BLOCK" | "ALERT";
};

export async function createRiskRule(input: RiskRuleCreateInput): Promise<void> {
  await axiosInstance.post(`${API_PREFIX}/riskrules`, {
    apiVersion: "pudans.tolim.cn/v1alpha1",
    kind: "RiskRule",
    metadata: { name: input.name },
    spec: {
      scene: input.scene,
      description: input.description || undefined,
      enabled: input.enabled,
      windowSeconds: input.windowSeconds,
      threshold: input.threshold,
      blockSeconds: input.action === "ALERT" ? 0 : input.blockSeconds,
      action: input.action,
    },
  });
}

export async function deleteRiskRule(name: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/riskrules/${encodeURIComponent(name)}`);
}

export async function fetchRiskBans(): Promise<RiskBanItem[]> {
  const response = await axiosInstance.get<RiskBanItem[]>(`${API_PREFIX}/riskrules/bans`);
  return Array.isArray(response.data) ? response.data : [];
}

export async function unbanRiskSubject(scene: string, subject: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/riskrules/unban`, { params: { scene, subject } });
}

export async function fetchRiskEvents(days = 7, limit = 200): Promise<RiskEventItem[]> {
  const from = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
  const [alerts, blocks] = await Promise.all([
    apiClient.auditLog.listAuditLogs({ operation: ALERT_OPERATION, limit, order: "desc", from }),
    apiClient.auditLog.listAuditLogs({ operation: BLOCK_OPERATION, limit, order: "desc", from }),
  ]);
  return [...(alerts.data.items ?? []), ...(blocks.data.items ?? [])]
    .map(toRiskEvent)
    .sort((left, right) => right.timestamp.localeCompare(left.timestamp));
}

export async function fetchRiskUnbans(days = 7, limit = 200): Promise<RiskUnbanItem[]> {
  const from = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
  const response = await apiClient.auditLog.listAuditLogs({
    operation: "RISK_UNBAN",
    limit,
    order: "desc",
    from,
  });
  return (response.data.items ?? []).map((record) => {
    const resourceName = record.resourceName ?? "";
    const separator = resourceName.indexOf(":");
    return {
      auditId: record.metadata?.name ?? "",
      scene: separator > 0 ? resourceName.slice(0, separator) : resourceName,
      subject: separator > 0 ? resourceName.slice(separator + 1) : "",
      timestamp: record.timestamp ?? "",
    };
  });
}

export async function fetchRiskEventTrend(days = 7): Promise<RiskTrendPoint[]> {
  const events = await fetchRiskEvents(days, 500);
  const counts = new Map<string, number>();
  for (const event of events) {
    if (!event.timestamp) {
      continue;
    }
    const date = beijingDateKey(event.timestamp) ?? event.timestamp.slice(0, 10);
    counts.set(date, (counts.get(date) ?? 0) + 1);
  }
  return [...counts.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, count]) => ({ date, count }));
}

export function riskRuleDisplayName(rule: RiskRuleItem): string {
  return (
    rule.metadata.annotations?.["rbac.authorization.pudans.tolim.cn/display-name"] ||
    rule.spec.description ||
    rule.metadata.name
  );
}

export function riskRuleDescription(rule: RiskRuleItem): string {
  return (
    rule.metadata.annotations?.["rbac.authorization.pudans.tolim.cn/description"] ||
    rule.spec.description ||
    ""
  );
}

export function resolveRiskApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "风险规则不存在或已被删除。",
    conflict: "风险规则名称已存在。",
  });
}

function toRiskEvent(record: {
  metadata?: { name?: string };
  operation?: string;
  resourceName?: string;
  ip?: string;
  uid?: string;
  requestMethod?: string;
  requestPath?: string;
  traceId?: string;
  result?: string;
  context?: Record<string, object>;
  timestamp?: string;
}): RiskEventItem {
  const context = (record.context ?? {}) as Record<string, unknown>;
  return {
    auditId: record.metadata?.name ?? "",
    operation: record.operation ?? "",
    scene: record.resourceName ?? "",
    subject: record.ip ?? "",
    uid: record.uid ?? "",
    username: typeof context.username === "string" ? context.username : "",
    path: record.requestPath ?? "",
    requestMethod: record.requestMethod ?? "",
    traceId: record.traceId ?? "",
    result: record.result ?? "",
    count: Number(context.count ?? 0),
    blockSeconds: Number(context.blockSeconds ?? 0),
    retryAfterMillis: Number(context.retryAfterMillis ?? 0),
    timestamp: record.timestamp ?? "",
  };
}
