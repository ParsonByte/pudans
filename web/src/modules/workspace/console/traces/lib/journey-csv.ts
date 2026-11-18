import { toCsv } from "@/lib/csv";
import { formatAuditExactTime } from "@/modules/workspace/console/audit/lib/api";
import { auditResultLabel } from "@/modules/workspace/console/audit/lib/audit-labels";

import type { JourneyItem } from "./api";

const EXPORT_HEADERS = [
  "用户",
  "用户 UID",
  "开始时间",
  "结束时间",
  "入口页",
  "出口页",
  "页面数",
  "操作数",
  "活跃段",
  "状态",
  "链路",
] as const;

export function journeysToCsv(journeys: JourneyItem[]): string {
  return toCsv(
    EXPORT_HEADERS,
    journeys.map((journey) => [
      journey.username || journey.uid,
      journey.uid,
      formatAuditExactTime(journey.start),
      formatAuditExactTime(journey.end),
      journey.entryPath || "",
      journey.exitPath || "",
      journey.pageCount ?? 0,
      journey.actionCount ?? 0,
      journey.sessionCount ?? 0,
      auditResultLabel(journey.status),
      journey.complete ? "完整" : "缺少入口",
    ]),
  );
}
