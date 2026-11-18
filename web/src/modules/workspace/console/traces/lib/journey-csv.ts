import type { AuditRecord } from "@pudans-dev/api-client";

import { toCsv } from "@/lib/csv";
import { formatAuditExactTime } from "@/modules/workspace/console/audit/lib/api";
import {
  auditOperationLabel,
  auditResourceLabel,
  auditResultLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";

import type { JourneyItem } from "./api";

const EXPORT_HEADERS = [
  "用户",
  "用户 UID",
  "链路开始",
  "链路结束",
  "入口页",
  "出口页",
  "页面序号",
  "页面路径",
  "页面来源",
  "页面去向",
  "页面开始",
  "页面结束",
  "页面结果",
  "操作时间",
  "操作类型",
  "资源类型",
  "资源名称",
  "操作结果",
  "请求方法",
  "请求路径",
  "审计 ID",
] as const;

export function journeyDetailsToCsv(journeys: JourneyItem[]): string {
  return toCsv(EXPORT_HEADERS, journeys.flatMap(journeyRows));
}

function journeyRows(journey: JourneyItem): unknown[][] {
  const steps = journey.steps ?? [];
  if (steps.length === 0) {
    return [summaryRow(journey)];
  }
  const rows: unknown[][] = [];
  let pageNumber = 0;
  for (const step of steps) {
    if (step.kind === "PAGE") {
      pageNumber += 1;
    }
    const page = step.page;
    const pageColumns = [
      journey.username || journey.uid,
      journey.uid,
      formatAuditExactTime(journey.start),
      formatAuditExactTime(journey.end),
      journey.entryPath || "",
      journey.exitPath || "",
      step.kind === "SESSION" ? "进入前" : pageNumber,
      pagePath(page),
      step.fromPath || "",
      step.toPath || "",
      formatAuditExactTime(step.start),
      formatAuditExactTime(step.end),
      page ? auditResultLabel(page.result) : "",
    ];
    const actions = step.actions ?? [];
    if (actions.length === 0) {
      rows.push([...pageColumns, "", "", "", "", "", "", "", ""]);
      continue;
    }
    for (const action of actions) {
      rows.push([...pageColumns, ...actionColumns(action)]);
    }
  }
  return rows;
}

function actionColumns(action: AuditRecord): unknown[] {
  return [
    formatAuditExactTime(action.timestamp),
    auditOperationLabel(action.operation),
    auditResourceLabel(action.resourceType),
    action.resourceName || "",
    auditResultLabel(action.result),
    action.requestMethod || "",
    action.requestPath || "",
    action.metadata.name,
  ];
}

function summaryRow(journey: JourneyItem): unknown[] {
  return [
    journey.username || journey.uid,
    journey.uid,
    formatAuditExactTime(journey.start),
    formatAuditExactTime(journey.end),
    journey.entryPath || "",
    journey.exitPath || "",
    "",
    "",
    "",
    "",
    "",
    "",
    auditResultLabel(journey.status),
    "",
    "",
    "",
    "",
    "",
    "",
    "",
    "",
  ];
}

function pagePath(page?: AuditRecord): string {
  if (page?.resourceType === "Page" && page.resourceName) {
    return page.resourceName;
  }
  return page?.requestPath || "";
}
