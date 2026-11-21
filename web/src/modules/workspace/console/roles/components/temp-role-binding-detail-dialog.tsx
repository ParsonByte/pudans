import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatBeijingDateTime } from "@/lib/date";

import type { TempRoleBindingRecord } from "../lib/api";
import {
  tempRoleBindingRoleNames,
  tempRoleStatusLabel,
  tempRoleStatusVariant,
} from "../lib/binding-model";

type TempRoleBindingDetailDialogProps = {
  binding: TempRoleBindingRecord | null;
  roleLabel: (roleName: string) => string;
  onClose: () => void;
};

export function TempRoleBindingDetailDialog({
  binding,
  roleLabel,
  onClose,
}: TempRoleBindingDetailDialogProps) {
  const spec = binding?.spec;

  return (
    <DetailDialog
      open={Boolean(binding)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title="临时角色绑定"
      description={
        binding ? <span className="font-mono text-xs">{binding.metadata.name}</span> : undefined
      }
      size="2xl"
      footer={
        <Button type="button" variant="outline" onClick={onClose}>
          关闭
        </Button>
      }
    >
      {binding && spec ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={tempRoleStatusVariant(spec.status)}>
              {tempRoleStatusLabel(spec.status)}
            </Badge>
            {spec.maxDurationSeconds ? (
              <span className="text-xs text-muted-foreground">
                最长 {Math.round(spec.maxDurationSeconds / 60)} 分钟
              </span>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <InfoRow label="用户" value={spec.username || "—"} />
            <InfoRow label="到期时间" value={formatBeijingDateTime(spec.expiresAt)} />
            <InfoRow label="申请人" value={spec.requestedBy || "—"} />
            <InfoRow label="审批人" value={spec.approvedBy || "—"} />
            <InfoRow label="审批时间" value={formatBeijingDateTime(spec.approvedAt)} />
            <InfoRow
              label="工单号"
              value={
                spec.ticketId ? <span className="font-mono text-xs">{spec.ticketId}</span> : "—"
              }
            />
            <InfoRow
              label="角色"
              value={
                <div className="flex flex-wrap gap-1">
                  {tempRoleBindingRoleNames(spec.roleNames).map((roleName) => (
                    <Badge key={roleName} variant="secondary">
                      {roleLabel(roleName)}
                    </Badge>
                  ))}
                </div>
              }
              className="sm:col-span-2"
            />
            <InfoRow label="申请原因" value={spec.reason || "—"} className="sm:col-span-2" />
            <InfoRow
              label="审批审计 ID"
              value={
                spec.approvalAuditId ? (
                  <span className="font-mono text-xs">{spec.approvalAuditId}</span>
                ) : (
                  "—"
                )
              }
            />
            <InfoRow
              label="撤销审计 ID"
              value={
                spec.revokeAuditId ? (
                  <span className="font-mono text-xs">{spec.revokeAuditId}</span>
                ) : (
                  "—"
                )
              }
            />
          </div>
        </div>
      ) : null}
    </DetailDialog>
  );
}
