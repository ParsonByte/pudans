import type { RecentOperation } from "@pudans-dev/api-client";

import { Badge } from "@/components/ui/badge";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { formatAuditTime } from "../lib/format";

type RecentOperationsProps = {
  operations: RecentOperation[];
};

export function RecentOperations({ operations }: RecentOperationsProps) {
  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="最近操作"
        description="审计日志中的最近记录"
        action={<Badge variant="secondary">{operations.length} 条</Badge>}
      />
      {operations.length === 0 ? (
        <ConsoleEmpty>暂无审计记录。</ConsoleEmpty>
      ) : (
        <ul className="divide-y">
          {operations.map((operation, index) => (
            <li
              key={operation.auditId ?? `${operation.timestamp}-${index}`}
              className="flex flex-col gap-3 px-5 py-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 space-y-1">
                <p className="truncate text-sm font-medium">
                  {auditOperationLabel(operation.operation)}
                  {operation.resourceType ? (
                    <span className="ms-2 text-xs font-normal text-muted-foreground">
                      {auditResourceLabel(operation.resourceType)}
                    </span>
                  ) : null}
                </p>
                <p className="truncate text-xs text-muted-foreground">
                  {operation.operator || "未知用户"}
                  {operation.resourceName ? ` · ${operation.resourceName}` : ""}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <AuditResultBadge result={operation.result} />
                <time
                  dateTime={operation.timestamp}
                  className="text-xs text-muted-foreground tabular-nums"
                >
                  {formatAuditTime(operation.timestamp)}
                </time>
              </div>
            </li>
          ))}
        </ul>
      )}
    </ConsoleSection>
  );
}
