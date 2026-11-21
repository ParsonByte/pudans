import {
  type AuditGraph,
  type AuditLogPage,
  type AuditNode,
  type AuditRecord,
  type AuditTrace,
  apiClient,
} from "@pudans-dev/api-client";

import {
  beijingDateTimeInputToIso,
  beijingDateTimeInputValue,
  beijingHourKey,
  formatBeijingDateTimeSeconds,
  startOfBeijingDay,
} from "@/lib/date";

export type AuditVolumePoint = {
  date: string;
  count: number;
};

export type AuditLogFilters = {
  from?: string;
  to?: string;
  operation?: string;
  result?: string;
  uid?: string;
  traceId?: string;
  traceFlowId?: string;
};

async function fetchAuditLogs(
  filters: AuditLogFilters & { cursor?: string; limit?: number; order?: "asc" | "desc" },
) {
  const response = await apiClient.auditLog.listAuditLogs({
    ...filters,
    limit: filters.limit ?? 100,
    order: filters.order ?? "desc",
  });
  return response.data;
}

export async function fetchAuditLogsPage(
  filters: AuditLogFilters & { page: number; size: number; order?: "asc" | "desc" },
): Promise<AuditLogPage> {
  const response = await apiClient.auditLog.listAuditLogs(filters);
  return response.data;
}

export function fetchTodayAuditLogs(limit = 500) {
  return fetchAuditLogs({ from: startOfBeijingDay(new Date()).toISOString(), limit });
}

export async function fetchAuditGraph(uid: string, date: string): Promise<AuditGraph> {
  const response = await apiClient.auditLog.getDailyAuditGraph({ uid, date });
  return response.data;
}

export async function fetchAuditTrace(auditId: string): Promise<AuditTrace> {
  const response = await apiClient.auditLog.getAuditTrace({ auditId });
  return response.data;
}

export type AuditGraphHourPoint = {
  hour: string;
  success: number;
  failed: number;
  other: number;
};

export function auditGraphHourly(nodes: readonly AuditNode[]): AuditGraphHourPoint[] {
  const buckets = new Map<string, AuditGraphHourPoint>();
  for (const node of nodes) {
    const key = beijingHourKey(node.timestamp);
    if (!key) {
      continue;
    }
    const bucket = buckets.get(key) ?? { hour: key, success: 0, failed: 0, other: 0 };
    if (node.result === "SUCCESS" || node.result === "SUCCEEDED") {
      bucket.success += 1;
    } else if (node.result === "FAILURE" || node.result === "FAILED" || node.result === "DENIED") {
      bucket.failed += 1;
    } else {
      bucket.other += 1;
    }
    buckets.set(key, bucket);
  }
  return [...buckets.values()].sort((left, right) => left.hour.localeCompare(right.hour));
}

type AuditGraphOperationCount = {
  operation: string;
  count: number;
};

export function auditGraphOperationCounts(nodes: readonly AuditNode[]): AuditGraphOperationCount[] {
  const counts = new Map<string, number>();
  for (const node of nodes) {
    if (!node.operation) {
      continue;
    }
    counts.set(node.operation, (counts.get(node.operation) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([operation, count]) => ({ operation, count }))
    .sort(
      (left, right) => right.count - left.count || left.operation.localeCompare(right.operation),
    );
}

export function auditVolumeByHour(records: readonly AuditRecord[]): AuditVolumePoint[] {
  const buckets = new Map<string, number>();
  for (const record of records) {
    if (!record.timestamp) {
      continue;
    }
    const key = beijingHourKey(record.timestamp);
    if (!key) {
      continue;
    }
    buckets.set(key, (buckets.get(key) ?? 0) + 1);
  }
  return [...buckets.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, count]) => ({ date, count }));
}

export function formatAuditHour(value: string): string {
  return value.length >= 16 && value[10] === "T" ? value.slice(11, 16) : value;
}

export function formatAuditExactTime(value?: string): string {
  return formatBeijingDateTimeSeconds(value);
}

export function defaultAuditTimeRange(): { from: string; to: string } {
  const now = new Date();
  return {
    from: beijingDateTimeInputValue(new Date(now.getTime() - 24 * 60 * 60 * 1000)),
    to: beijingDateTimeInputValue(now),
  };
}

export function todayAuditTimeRange(): { from: string; to: string } {
  const now = new Date();
  return {
    from: beijingDateTimeInputValue(startOfBeijingDay(now)),
    to: beijingDateTimeInputValue(now),
  };
}

export function localDateTimeToIso(value: string): string | undefined {
  return beijingDateTimeInputToIso(value);
}
