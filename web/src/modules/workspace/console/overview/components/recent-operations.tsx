import type { RecentOperation } from "@pudans-dev/api-client";

import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { ConsoleState } from "@/modules/workspace/console/components/console-state";

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
        <ConsoleState variant="empty" title="暂无审计记录。" />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="px-5">时间</TableHead>
              <TableHead className="px-5">操作</TableHead>
              <TableHead className="px-5">资源</TableHead>
              <TableHead className="px-5">操作人</TableHead>
              <TableHead className="px-5">结果</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {operations.map((operation, index) => (
              <TableRow key={operation.auditId ?? `${operation.timestamp}-${index}`}>
                <TableCell className="px-5 py-2.5 text-xs text-muted-foreground tabular-nums">
                  <time dateTime={operation.timestamp}>{formatAuditTime(operation.timestamp)}</time>
                </TableCell>
                <TableCell className="px-5 py-2.5 text-sm">
                  {auditOperationLabel(operation.operation)}
                </TableCell>
                <TableCell className="px-5 py-2.5">
                  <div className="min-w-0">
                    <p className="text-xs text-muted-foreground">
                      {operation.resourceType ? auditResourceLabel(operation.resourceType) : "—"}
                    </p>
                    <LongText className="max-w-64 text-sm">
                      {operation.resourceName || "—"}
                    </LongText>
                  </div>
                </TableCell>
                <TableCell className="px-5 py-2.5 text-sm">
                  {operation.operator || "未知用户"}
                </TableCell>
                <TableCell className="px-5 py-2.5">
                  <AuditResultBadge result={operation.result} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </ConsoleSection>
  );
}
