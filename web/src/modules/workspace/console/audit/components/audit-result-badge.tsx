import { Badge } from "@/components/ui/badge";

import { auditResultLabel } from "../lib/audit-labels";

type AuditResultBadgeProps = {
  result?: string;
};

export function AuditResultBadge({ result }: AuditResultBadgeProps) {
  if (result === "SUCCESS") {
    return <Badge variant="secondary">成功</Badge>;
  }
  if (result === "FAILURE") {
    return (
      <Badge variant="outline" className="border-amber-500/40 text-amber-600 dark:text-amber-400">
        异常
      </Badge>
    );
  }
  if (result === "FAILED") {
    return <Badge variant="destructive">失败</Badge>;
  }
  if (result === "DENIED") {
    return <Badge variant="destructive">拒绝</Badge>;
  }
  if (result) {
    return <Badge variant="outline">{auditResultLabel(result)}</Badge>;
  }
  return <Badge variant="outline">未知</Badge>;
}
