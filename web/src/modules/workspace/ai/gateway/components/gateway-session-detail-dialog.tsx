import { useQuery } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";

import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { formatRelativeTime } from "../../routing/lib/model";
import type { AiGatewaySession } from "../../sessions/lib/api";
import { sessionStatusBadgeVariant, sessionStatusLabel } from "../../sessions/lib/model";
import { fetchGatewaySession, resolveGatewayApiError } from "../lib/api";

type GatewaySessionDetailDialogProps = {
  session: AiGatewaySession;
  onClose: () => void;
};

export function GatewaySessionDetailDialog({ session, onClose }: GatewaySessionDetailDialogProps) {
  const sessionQuery = useQuery({
    queryKey: ["ai-gateway-session", session.id],
    queryFn: () => fetchGatewaySession(session.id),
    initialData: session,
    retry: false,
  });
  const current = sessionQuery.data;

  return (
    <DetailDialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={`网关会话 #${session.id}`}
      description={<span className="font-mono text-xs break-all">{current.sessionKey}</span>}
      size="2xl"
      footer={
        <>
          <Button
            type="button"
            variant="outline"
            disabled={sessionQuery.isFetching}
            onClick={() => void sessionQuery.refetch()}
          >
            {sessionQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
          <Button type="button" onClick={onClose}>
            关闭
          </Button>
        </>
      }
    >
      {sessionQuery.isError ? (
        <ConsoleState
          variant="error"
          title="无法加载网关会话"
          description={resolveGatewayApiError(sessionQuery.error)}
          onRetry={() => void sessionQuery.refetch()}
        />
      ) : sessionQuery.isPending ? (
        <ConsoleBlockSkeleton rows={6} className="grid gap-3 sm:grid-cols-2" itemClassName="h-14" />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          <InfoRow
            label="状态"
            value={
              <Badge variant={sessionStatusBadgeVariant(current.status)}>
                {sessionStatusLabel(current.status)}
              </Badge>
            }
          />
          <InfoRow label="来源" value={session.agentSource} />
          <InfoRow
            label="空间"
            value={<span className="font-mono text-xs break-all">{current.spaceId}</span>}
          />
          <InfoRow
            label="用户"
            value={<span className="font-mono text-xs break-all">{current.userId}</span>}
          />
          <InfoRow
            label="会话键"
            value={<span className="font-mono text-xs break-all">{current.sessionKey}</span>}
          />
          <InfoRow
            label="会话 ID"
            value={<span className="font-mono text-xs break-all">{current.sessionId || "—"}</span>}
          />
          <InfoRow label="团队" value={current.teamId} />
          <InfoRow label="Agent" value={current.agentId} />
          <InfoRow
            label="任务"
            value={<span className="font-mono text-xs break-all">{current.taskId || "—"}</span>}
          />
          <InfoRow
            label="对话"
            value={
              <span className="font-mono text-xs">
                {current.conversationId == null ? "—" : `#${current.conversationId}`}
              </span>
            }
          />
          <InfoRow label="创建时间" value={formatUserDateTime(current.createdAt)} />
          <InfoRow
            label="更新时间"
            value={
              <span>
                {formatUserDateTime(current.updatedAt)}
                <span className="ms-2 text-xs text-muted-foreground">
                  {formatRelativeTime(current.updatedAt)}
                </span>
              </span>
            }
          />
        </div>
      )}
    </DetailDialog>
  );
}
