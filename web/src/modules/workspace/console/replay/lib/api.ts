import { type AuditArchiveStats, type AuditLogPage, apiClient } from "@pudans-dev/api-client";

import {
  type TraceFlowFilters,
  fetchTraceFlowsPage,
} from "@/modules/workspace/console/traces/lib/api";

export type ArchivedLogFilters = {
  from?: string;
  to?: string;
  uid?: string;
  operation?: string;
  result?: string;
  traceId?: string;
  traceFlowId?: string;
  page: number;
  size: number;
};

export async function fetchReplayFlows(filters: Omit<TraceFlowFilters, "summary">) {
  return fetchTraceFlowsPage({ ...filters, summary: true });
}

export async function fetchReplay(traceFlowId: string) {
  const response = await apiClient.auditLog.replayAuditFlow({ traceFlowId });
  return response.data;
}

export async function fetchArchiveStats(): Promise<AuditArchiveStats> {
  const response = await apiClient.auditLog.getAuditArchiveStats();
  return response.data;
}

export async function fetchArchivedLogsPage(filters: ArchivedLogFilters): Promise<AuditLogPage> {
  const response = await apiClient.auditLog.listArchivedAuditLogs(filters);
  return response.data;
}

export async function archiveAuditLogs(before: string) {
  const response = await apiClient.auditLog.archiveAuditLogs({
    auditArchiveRequest: { before },
  });
  return response.data;
}
