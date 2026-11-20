import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { Loader2, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { MemoryErrorState } from "../../memory/components/memory-states";
import { GatewaySessionTable } from "../../sessions/components/gateway-session-table";
import {
  type AiGatewaySession,
  deleteGatewaySession,
  fetchGatewaySessions,
  resolveAiSessionApiError,
} from "../../sessions/lib/api";
import { DEFAULT_SESSION_PAGE_SIZE, sessionStatusLabel } from "../../sessions/lib/model";
import { useCursorPagination } from "../../sessions/lib/use-cursor-pagination";
import { UsageCursorPagination } from "../../usage/components/usage-cursor-pagination";

type GatewaySessionsProps = {
  canDelete: boolean;
  sessionsPath: string | undefined;
};

export function GatewaySessions({ canDelete, sessionsPath }: GatewaySessionsProps) {
  const queryClient = useQueryClient();
  const pagination = useCursorPagination(DEFAULT_SESSION_PAGE_SIZE);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
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
  const statusOptions = [...new Set(items.map((session) => session.status))].sort((left, right) =>
    sessionStatusLabel(left).localeCompare(sessionStatusLabel(right), "zh-CN"),
  );
  const keyword = search.trim().toLowerCase();
  const filtered = items.filter((session) => {
    if (statusFilter !== "all" && session.status !== statusFilter) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return `${session.sessionKey} ${session.sessionId ?? ""} ${session.teamId} ${
      session.agentId
    } ${session.agentSource} ${session.conversationId ?? ""}`
      .toLowerCase()
      .includes(keyword);
  });

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
    <ConsoleSection className="overflow-hidden">
      <ConsoleSectionHeader
        title="网关会话"
        description="网关把 Agent 会话映射到平台会话；删除绑定不会删除记忆数据。"
        action={
          <div className="flex items-center gap-2">
            {sessionsPath ? (
              <Button type="button" variant="ghost" size="sm" asChild>
                <Link to={sessionsPath}>会话排障</Link>
              </Button>
            ) : null}
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
          </div>
        }
      />

      {items.length > 0 ? (
        <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3">
          <div className="relative">
            <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="搜索会话键、团队或 Agent"
              className="h-8 w-64 ps-9"
              aria-label="搜索网关会话"
            />
          </div>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="h-8 w-32">
              <SelectValue placeholder="全部状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部状态</SelectItem>
              {statusOptions.map((status) => (
                <SelectItem key={status} value={status}>
                  {sessionStatusLabel(status)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <span className="text-[11px] text-muted-foreground">
            本页 {filtered.length} / {items.length} 条
          </span>
        </div>
      ) : null}

      {sessionsQuery.isPending ? (
        <div className="space-y-2 px-5 py-4">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-10 w-full" />
          ))}
        </div>
      ) : sessionsQuery.isError ? (
        <div className="space-y-2">
          <MemoryErrorState
            title="无法加载网关会话"
            message={resolveAiSessionApiError(sessionsQuery.error)}
            onRetry={() => void sessionsQuery.refetch()}
          />
          {sessionsPath ? (
            <p className="px-5 pb-4 text-xs text-muted-foreground">
              网关会话排障需要管理员授予对应操作权限，可前往
              <Link to={sessionsPath} className="mx-1 font-medium text-primary hover:underline">
                会话页
              </Link>
              查看。
            </p>
          ) : null}
        </div>
      ) : items.length === 0 ? (
        <ConsoleEmpty>
          还没有网关会话绑定。通过 AI 网关发起请求后，绑定关系会出现在这里。
        </ConsoleEmpty>
      ) : filtered.length === 0 ? (
        <ConsoleEmpty>当前页没有匹配的网关会话，试试调整筛选条件或翻页。</ConsoleEmpty>
      ) : (
        <div className="overflow-x-auto">
          <GatewaySessionTable
            sessions={filtered}
            canDelete={canDelete}
            onDelete={setDeleteTarget}
          />
        </div>
      )}

      {sessionsQuery.isSuccess && items.length > 0 ? (
        <div className="border-t px-5 py-3">
          <UsageCursorPagination
            page={pagination.page + 1}
            pageSize={pagination.pageSize}
            hasPrevious={pagination.page > 0}
            hasNext={hasNext}
            onPrevious={pagination.goPrevious}
            onNext={() => pagination.goNext(items.at(-1)?.id)}
            onPageSizeChange={pagination.setPageSize}
            disabled={refreshing}
          />
        </div>
      ) : null}

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
    </ConsoleSection>
  );
}
