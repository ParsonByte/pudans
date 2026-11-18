import { type AuditLogPage, type AuditRecord, apiClient } from "@pudans-dev/api-client";
import { format, parseISO, startOfDay } from "date-fns";

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
  return fetchAuditLogs({ from: startOfDay(new Date()).toISOString(), limit });
}

export function auditVolumeByHour(records: readonly AuditRecord[]): AuditVolumePoint[] {
  const buckets = new Map<string, number>();
  for (const record of records) {
    if (!record.timestamp) {
      continue;
    }
    const date = parseISO(record.timestamp);
    if (Number.isNaN(date.getTime())) {
      continue;
    }
    const key = format(date, "yyyy-MM-dd'T'HH:00:00");
    buckets.set(key, (buckets.get(key) ?? 0) + 1);
  }
  return [...buckets.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, count]) => ({ date, count }));
}

export function formatAuditHour(value: string): string {
  const date = parseISO(value);
  return Number.isNaN(date.getTime()) ? value : format(date, "HH:mm");
}

export function formatAuditExactTime(value?: string): string {
  if (!value) {
    return "—";
  }
  const date = parseISO(value);
  return Number.isNaN(date.getTime()) ? value : format(date, "yyyy-MM-dd HH:mm:ss");
}

function toLocalDateTimeInput(value: Date): string {
  return format(value, "yyyy-MM-dd'T'HH:mm");
}

export function defaultAuditTimeRange(): { from: string; to: string } {
  const now = new Date();
  return {
    from: toLocalDateTimeInput(new Date(now.getTime() - 24 * 60 * 60 * 1000)),
    to: toLocalDateTimeInput(now),
  };
}

export function todayAuditTimeRange(): { from: string; to: string } {
  const now = new Date();
  return {
    from: toLocalDateTimeInput(startOfDay(now)),
    to: toLocalDateTimeInput(now),
  };
}

export function localDateTimeToIso(value: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}
