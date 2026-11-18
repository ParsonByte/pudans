import { type AuditJourneyPage, type AuditTracePage, apiClient } from "@pudans-dev/api-client";

export type JourneyItem = NonNullable<AuditJourneyPage["items"]>[number] & {
  uid: string;
};

export type JourneyFilters = {
  from?: string;
  to?: string;
  uid?: string;
  page: number;
  size: number;
  summary?: boolean;
};

export async function fetchJourneysPage(filters: JourneyFilters): Promise<AuditJourneyPage> {
  const response = await apiClient.auditLog.listAuditJourneys(filters);
  return response.data;
}

export type TraceFlowFilters = {
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

export async function fetchTraceFlowsPage(filters: TraceFlowFilters): Promise<AuditTracePage> {
  const response = await apiClient.auditLog.listAuditTraceFlows(filters);
  return response.data;
}
