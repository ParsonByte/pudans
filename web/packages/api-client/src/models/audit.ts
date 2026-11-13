import type {
  AuditGraph,
  AuditLogPage,
  AuditRecord,
  AuditReplay,
  AuditTrace,
  ReplayStep,
  TraceStep,
} from "./generated";

export type {
  AuditGraph,
  AuditLogPage,
  AuditRecord,
  AuditReplay,
  AuditTrace,
  ReplayStep,
  TraceStep,
};

export interface ListAuditLogsParams {
  cursor?: string;
  limit?: number;
  uid?: string;
  operation?: string;
  result?: string;
  traceId?: string;
  traceFlowId?: string;
  from?: string;
  to?: string;
}
