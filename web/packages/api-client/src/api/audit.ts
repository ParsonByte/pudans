import { http } from "../../entry/api-client";
import type { AuditGraph, AuditLogPage, AuditReplay, AuditTrace, ListAuditLogsParams } from "../models/audit";

const CONSOLE_API_BASE = "/apis/console.api.pudans.tolim.cn/v1alpha1";

/** GET /console/auditlogs */
export function listAuditLogs(params: ListAuditLogsParams = {}): Promise<AuditLogPage> {
  return http
    .get<AuditLogPage>(`${CONSOLE_API_BASE}/auditlogs`, { params })
    .then((response) => response.data);
}

/** GET /console/auditlogs/{auditId}/trace */
export function getAuditTrace(auditId: string): Promise<AuditTrace> {
  return http
    .get<AuditTrace>(`${CONSOLE_API_BASE}/auditlogs/${encodeURIComponent(auditId)}/trace`)
    .then((response) => response.data);
}

/** GET /console/auditlogs/graph */
export function getDailyAuditGraph(uid: string, date: string): Promise<AuditGraph> {
  return http
    .get<AuditGraph>(`${CONSOLE_API_BASE}/auditlogs/graph`, { params: { uid, date } })
    .then((response) => response.data);
}

/** GET /console/auditlogs/replay/{traceFlowId} */
export function replayAuditFlow(traceFlowId: string): Promise<AuditReplay> {
  return http
    .get<AuditReplay>(`${CONSOLE_API_BASE}/auditlogs/replay/${encodeURIComponent(traceFlowId)}`)
    .then((response) => response.data);
}
