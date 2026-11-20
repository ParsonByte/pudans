import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { CursorPager } from "@/modules/workspace/ai/memory/components/cursor-pager";
import {
  MemoryErrorState,
  MemoryLoadingState,
} from "@/modules/workspace/ai/memory/components/memory-states";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import {
  type AiGatewaySession,
  deleteGatewaySession,
  fetchGatewaySessions,
  resolveAiSessionApiError,
} from "../lib/api";
import { DEFAULT_SESSION_PAGE_SIZE } from "../lib/model";
import { useCursorPagination } from "../lib/use-cursor-pagination";
import { GatewaySessionTable } from "./gateway-session-table";

type GatewaySessionsTabProps = {
  canDelete: boolean;
};

export function GatewaySessionsTab({ canDelete }: GatewaySessionsTabProps) {
  const queryClient = useQueryClient();
  const pagination = useCursorPagination(DEFAULT_SESSION_PAGE_SIZE);
  const [deleteTarget, setDeleteTarget] = useState<AiGatewaySession | null>(null);

  const sessionsQuery = useQuery({
    queryKey: [
      "ai-sessions",
      "gateway-sessions",
      pagination.pageSize,
      pagination.cursor ?? "start",
    ],
    queryFn: () =>
      fetchGatewaySessions({ limit: pagination.pageSize, beforeId: pagination.cursor }),
    retry: false,
  });

  const items = sessionsQuery.data ?? [];
  const deleteMutation = useMutation({
    mutationFn: (session: AiGatewaySession) => deleteGatewaySession(session.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-sessions", "gateway-sessions"] });
      setDeleteTarget(null);
      if (items.length === 1 && pagination.page > 0) {
        pagination.goPrevious();
      }
      toast.success("网关会话绑定已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveAiSessionApiError(error));
    },
  });

  const refreshing = sessionsQuery.isFetching;
  const hasNext = items.length === pagination.pageSize;

  return (
    <div className="flex flex-col gap-5">
      <ConsoleSection className="overflow-hidden">
        <ConsoleSectionHeader
          title="网关会话"
          description="AI 网关把 Agent 会话映射到平台会话。删除绑定不会删除记忆数据。"
          action={
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void sessionsQuery.refetch()}
              disabled={refreshing}
            >
              {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          }
        />

        {sessionsQuery.isPending ? (
          <MemoryLoadingState rows={5} />
        ) : sessionsQuery.isError ? (
          <MemoryErrorState
            title="无法加载网关会话"
            message={resolveAiSessionApiError(sessionsQuery.error)}
            onRetry={() => void sessionsQuery.refetch()}
          />
        ) : items.length === 0 ? (
          <ConsoleEmpty>
            还没有网关会话绑定。通过 AI 网关发起请求后，绑定关系会出现在这里。
          </ConsoleEmpty>
        ) : (
          <GatewaySessionTable sessions={items} canDelete={canDelete} onDelete={setDeleteTarget} />
        )}

        {sessionsQuery.isSuccess && items.length > 0 ? (
          <CursorPager
            page={pagination.page}
            pageSize={pagination.pageSize}
            hasNext={hasNext}
            itemCount={items.length}
            loading={refreshing}
            onPrevious={pagination.goPrevious}
            onNext={() => pagination.goNext(items.at(-1)?.id)}
            onPageSizeChange={pagination.setPageSize}
          />
        ) : null}
      </ConsoleSection>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !deleteMutation.isPending) {
            setDeleteTarget(null);
          }
        }}
        title="删除网关会话绑定"
        desc={
          deleteTarget
            ? `确定删除会话键「${deleteTarget.sessionKey}」的网关绑定？记忆数据不会被删除。`
            : ""
        }
        destructive
        confirmText="删除"
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </div>
  );
}
