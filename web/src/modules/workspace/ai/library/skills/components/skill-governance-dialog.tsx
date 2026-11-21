import { useQuery } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";

import { DetailDialog } from "@/components/detail-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatBeijingDateTime } from "@/lib/date";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { resolveLibraryApiError } from "../../lib/api";
import {
  SKILL_AUDIT_QUERY_KEY,
  SKILL_USAGE_QUERY_KEY,
  fetchSkillAudit,
  fetchSkillUsage,
} from "../lib/api";
import {
  SKILL_USAGE_STATE_LABELS,
  skillAuditDecisionLabel,
  skillAuditOperationLabel,
} from "../lib/model";

const SKILL_AUDIT_LIMIT = 100;

type SkillGovernanceDialogProps = {
  onClose: () => void;
};

export function SkillGovernanceDialog({ onClose }: SkillGovernanceDialogProps) {
  const usageQuery = useQuery({
    queryKey: SKILL_USAGE_QUERY_KEY,
    queryFn: fetchSkillUsage,
    retry: false,
  });
  const auditQuery = useQuery({
    queryKey: SKILL_AUDIT_QUERY_KEY,
    queryFn: () => fetchSkillAudit(SKILL_AUDIT_LIMIT),
    retry: false,
  });
  const usage = usageQuery.data ?? [];
  const audit = [...(auditQuery.data ?? [])].reverse();
  const refreshing = usageQuery.isFetching || auditQuery.isFetching;

  return (
    <DetailDialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title="技能治理"
      description="查看技能使用统计与 Curator 审计记录；晋升与归档在技能列表中操作。"
      size="4xl"
      footer={
        <>
          <Button
            type="button"
            variant="outline"
            disabled={refreshing}
            onClick={() => {
              void usageQuery.refetch();
              void auditQuery.refetch();
            }}
          >
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
          <Button type="button" onClick={onClose}>
            关闭
          </Button>
        </>
      }
    >
      <Tabs defaultValue="usage">
        <TabsList>
          <TabsTrigger value="usage">使用统计</TabsTrigger>
          <TabsTrigger value="audit">审计记录</TabsTrigger>
        </TabsList>

        <TabsContent value="usage" className="space-y-3">
          <p className="text-xs text-muted-foreground">
            使用记录按最近活动排序；该接口不返回技能名称，统计无法归集到具体技能。
          </p>
          {usageQuery.isError ? (
            <ConsoleState
              variant="error"
              title="无法加载技能使用统计"
              description={resolveLibraryApiError(usageQuery.error, "技能使用统计")}
              onRetry={() => void usageQuery.refetch()}
            />
          ) : usageQuery.isPending ? (
            <ConsoleBlockSkeleton rows={4} itemClassName="h-10" />
          ) : usage.length === 0 ? (
            <ConsoleState variant="empty" title="暂无技能使用记录" />
          ) : (
            <div className="overflow-x-auto border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="px-3">状态</TableHead>
                    <TableHead className="px-3">使用</TableHead>
                    <TableHead className="px-3">查看</TableHead>
                    <TableHead className="px-3">修改</TableHead>
                    <TableHead className="px-3">创建者</TableHead>
                    <TableHead className="px-3">晋升者</TableHead>
                    <TableHead className="px-3">环境</TableHead>
                    <TableHead className="px-3">创建时间</TableHead>
                    <TableHead className="px-3">最近活动</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {usage.map((record, index) => (
                    <TableRow key={`${record.createdAt}-${index}`}>
                      <TableCell className="px-3">
                        <Badge variant={record.state === "ACTIVE" ? "secondary" : "outline"}>
                          {SKILL_USAGE_STATE_LABELS[record.state]}
                        </Badge>
                      </TableCell>
                      <TableCell className="px-3 tabular-nums">{record.useCount}</TableCell>
                      <TableCell className="px-3 tabular-nums">{record.viewCount}</TableCell>
                      <TableCell className="px-3 tabular-nums">{record.patchCount}</TableCell>
                      <TableCell className="px-3 font-mono text-xs">
                        {record.createdBy || "—"}
                      </TableCell>
                      <TableCell className="px-3 font-mono text-xs">
                        {record.promotedBy || "—"}
                      </TableCell>
                      <TableCell className="px-3 text-xs">
                        {record.environments.length > 0 ? record.environments.join(", ") : "—"}
                      </TableCell>
                      <TableCell className="px-3 text-xs">
                        {formatBeijingDateTime(record.createdAt)}
                      </TableCell>
                      <TableCell className="px-3 text-xs">
                        {formatBeijingDateTime(record.lastActivityAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>

        <TabsContent value="audit" className="space-y-3">
          {auditQuery.isError ? (
            <ConsoleState
              variant="error"
              title="无法加载技能审计记录"
              description={resolveLibraryApiError(auditQuery.error, "技能审计记录")}
              onRetry={() => void auditQuery.refetch()}
            />
          ) : auditQuery.isPending ? (
            <ConsoleBlockSkeleton rows={4} itemClassName="h-10" />
          ) : audit.length === 0 ? (
            <ConsoleState variant="empty" title="暂无技能审计记录" />
          ) : (
            <div className="overflow-x-auto border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="px-3">时间</TableHead>
                    <TableHead className="px-3">操作人</TableHead>
                    <TableHead className="px-3">操作</TableHead>
                    <TableHead className="px-3">目标</TableHead>
                    <TableHead className="px-3">决定</TableHead>
                    <TableHead className="px-3">详情</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {audit.map((entry, index) => (
                    <TableRow key={`${entry.at}-${entry.target}-${index}`}>
                      <TableCell className="px-3 text-xs">{formatUserDateTime(entry.at)}</TableCell>
                      <TableCell className="px-3 font-mono text-xs">{entry.actor}</TableCell>
                      <TableCell className="px-3 text-xs">
                        {skillAuditOperationLabel(entry.operation)}
                      </TableCell>
                      <TableCell className="px-3 font-mono text-xs">{entry.target}</TableCell>
                      <TableCell className="px-3">
                        <Badge
                          variant={
                            entry.decision === "approved"
                              ? "secondary"
                              : entry.decision === "rejected" || entry.decision === "invalid"
                                ? "destructive"
                                : "outline"
                          }
                        >
                          {skillAuditDecisionLabel(entry.decision)}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-64 px-3 text-xs text-muted-foreground">
                        {entry.detail || "—"}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>
      </Tabs>
    </DetailDialog>
  );
}
