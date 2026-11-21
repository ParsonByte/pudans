import { useMemo, useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Download, Ellipsis, RefreshCw, RotateCcw, Snowflake, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { downloadBlob } from "@/lib/csv";
import { beijingTimestamp } from "@/lib/date";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import {
  type MemoryCorrectionInput,
  type MemoryRebuildInput,
  type MemoryRecordItem,
  type MemoryRecordStatus,
  type MemoryRecordType,
  createMemoryCorrectionRequest,
  deleteMemoryRecord,
  exportMemoryRecords,
  flushMemoryPipeline,
  freezeMemoryRecord,
  rebuildMemoryRecords,
  resolveMemoryApiError,
  restoreMemoryRecord,
} from "../lib/api";
import {
  MEMORY_RECORD_STATUS_LABELS,
  MEMORY_RECORD_STATUS_OPTIONS,
  MEMORY_REVIEW_PAGE_SIZE,
  MEMORY_TYPE_LABELS,
  memoryRecordTimestamp,
} from "../lib/model";
import { invalidateMemoryReviewQueries, useMemoryPipeline, useMemoryRecords } from "../lib/queries";
import { MemoryConfirmDialog } from "./memory-confirm-dialogs";
import {
  MemoryCorrectionDialog,
  MemoryFlushDialog,
  MemoryRebuildDialog,
  MemoryRecordDeleteDialog,
  MemoryRecordDetailDialog,
} from "./memory-review-dialogs";
import { MemoryEmptyState, MemoryErrorState } from "./memory-states";

type RecordAction = "freeze" | "restore";

type MemoryRecordsPanelProps = {
  currentUserId?: string;
  canExecute: boolean;
  canDelete: boolean;
  canExport: boolean;
};

type RecordFilters = {
  teamId: string;
  agentId: string;
  userId: string;
  type: string;
  status: string;
};

const EMPTY_FILTERS: RecordFilters = {
  teamId: "",
  agentId: "",
  userId: "",
  type: "ALL",
  status: "ALL",
};

const RECORD_TYPE_OPTIONS = Object.entries(MEMORY_TYPE_LABELS) as [MemoryRecordType, string][];

export function MemoryRecordsPanel({
  currentUserId,
  canExecute,
  canDelete,
  canExport,
}: MemoryRecordsPanelProps) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<RecordFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<RecordFilters>(EMPTY_FILTERS);
  const [confirming, setConfirming] = useState<{
    record: MemoryRecordItem;
    action: RecordAction;
  } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<MemoryRecordItem | null>(null);
  const [correctionTarget, setCorrectionTarget] = useState<MemoryRecordItem | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [rebuildOpen, setRebuildOpen] = useState(false);
  const [flushOpen, setFlushOpen] = useState(false);

  const query = useMemoryRecords(
    {
      teamId: filters.teamId.trim() || undefined,
      agentId: filters.agentId.trim() || undefined,
      userId: filters.userId.trim() || undefined,
      type: filters.type === "ALL" ? undefined : filters.type,
      status: filters.status === "ALL" ? undefined : filters.status,
      limit: MEMORY_REVIEW_PAGE_SIZE,
    },
    true,
  );
  const pipelineQuery = useMemoryPipeline(true);

  const exportQuery = useMemo(
    () => ({
      teamId: filters.teamId.trim() || undefined,
      agentId: filters.agentId.trim() || undefined,
      userId: filters.userId.trim() || undefined,
      type: filters.type === "ALL" ? undefined : filters.type,
      status: filters.status === "ALL" ? undefined : filters.status,
    }),
    [filters],
  );

  const actionMutation = useMutation({
    mutationFn: async (input: { record: MemoryRecordItem; action: RecordAction }) => {
      return input.action === "freeze"
        ? freezeMemoryRecord(input.record.id)
        : restoreMemoryRecord(input.record.id);
    },
    onSuccess: async (_, input) => {
      await invalidateMemoryReviewQueries(queryClient);
      toast.success(input.action === "freeze" ? "记忆已冻结" : "记忆已恢复");
      setConfirming(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (input: { record: MemoryRecordItem; reason: string }) =>
      deleteMemoryRecord(input.record.id, input.reason),
    onSuccess: async () => {
      await invalidateMemoryReviewQueries(queryClient);
      toast.success("记忆记录已删除");
      setDeleteTarget(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const correctionMutation = useMutation({
    mutationFn: (input: { record: MemoryRecordItem; request: MemoryCorrectionInput }) =>
      createMemoryCorrectionRequest(String(input.record.id), input.request),
    onSuccess: async () => {
      await invalidateMemoryReviewQueries(queryClient);
      toast.success("记忆修正申请已提交");
      setCorrectionTarget(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const rebuildMutation = useMutation({
    mutationFn: (input: MemoryRebuildInput) => rebuildMemoryRecords(input),
    onSuccess: async (result) => {
      await invalidateMemoryReviewQueries(queryClient);
      toast.success(`${result.level} 记忆重建已排队`);
      setRebuildOpen(false);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const flushMutation = useMutation({
    mutationFn: (conversationId: number) => flushMemoryPipeline(conversationId),
    onSuccess: async () => {
      await invalidateMemoryReviewQueries(queryClient);
      toast.success("会话已加入 L1 抽取队列");
      setFlushOpen(false);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const exportMutation = useMutation({
    mutationFn: async () => {
      const blob = await exportMemoryRecords({ ...exportQuery, limit: 5000 });
      downloadBlob(`memory-records-${beijingTimestamp()}.csv`, blob);
    },
    onSuccess: () => toast.success("记忆记录 CSV 已开始下载"),
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const records = query.data ?? [];
  const pipeline = pipelineQuery.data;
  const confirmCopy = confirming
    ? confirming.action === "freeze"
      ? {
          title: "冻结记忆",
          description: `冻结后记忆 #${confirming.record.id} 不再参与召回与聚合。`,
          label: "冻结",
        }
      : {
          title: "恢复记忆",
          description: `将从最近一次删除快照恢复记忆 #${confirming.record.id} 的内容与状态。`,
          label: "恢复",
        }
    : null;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border bg-card px-4 py-3 text-xs">
        <span className="text-sm font-medium">记忆流水线</span>
        {pipelineQuery.isLoading ? (
          <Skeleton className="h-4 w-64" />
        ) : pipelineQuery.isError ? (
          <span className="text-destructive">{resolveMemoryApiError(pipelineQuery.error)}</span>
        ) : pipeline ? (
          <>
            <PipelineCount label="待处理任务" value={pipeline.pendingTasks} />
            <PipelineCount label="运行中" value={pipeline.runningTasks} />
            <PipelineCount label="已完成" value={pipeline.doneTasks} />
            <PipelineCount label="失败" value={pipeline.failedTasks} />
            <PipelineCount label="待抽取会话" value={pipeline.pendingConversations} />
          </>
        ) : null}
        <div className="ms-auto flex items-center gap-1">
          {canExecute ? (
            <>
              <Button type="button" variant="ghost" size="sm" onClick={() => setFlushOpen(true)}>
                <RefreshCw />
                刷新流水线
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => setRebuildOpen(true)}>
                <RotateCcw />
                重建记忆
              </Button>
            </>
          ) : null}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Input
          value={draft.teamId}
          onChange={(event) => setDraft({ ...draft, teamId: event.target.value })}
          placeholder="团队 ID"
          className="h-8 w-32 text-xs"
        />
        <Input
          value={draft.agentId}
          onChange={(event) => setDraft({ ...draft, agentId: event.target.value })}
          placeholder="Agent ID"
          className="h-8 w-32 text-xs"
        />
        <Input
          value={draft.userId}
          onChange={(event) => setDraft({ ...draft, userId: event.target.value })}
          placeholder="用户 ID"
          className="h-8 w-36 text-xs"
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              setFilters(draft);
            }
          }}
        />
        <Select value={draft.type} onValueChange={(value) => setDraft({ ...draft, type: value })}>
          <SelectTrigger className="w-32" size="sm" aria-label="按记忆类型筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部类型</SelectItem>
            {RECORD_TYPE_OPTIONS.map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={draft.status}
          onValueChange={(value) => setDraft({ ...draft, status: value })}
        >
          <SelectTrigger className="w-28" size="sm" aria-label="按记忆状态筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部状态</SelectItem>
            {MEMORY_RECORD_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button type="button" size="sm" onClick={() => setFilters(draft)}>
          查询
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => {
            setDraft(EMPTY_FILTERS);
            setFilters(EMPTY_FILTERS);
          }}
        >
          重置
        </Button>
        <div className="ms-auto flex items-center gap-1">
          {canExport ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={exportMutation.isPending}
              onClick={() => exportMutation.mutate()}
            >
              <Download />
              导出 CSV
            </Button>
          ) : null}
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={query.isFetching}
            onClick={() => query.refetch()}
          >
            <RefreshCw className={query.isFetching ? "animate-spin" : ""} />
            刷新
          </Button>
        </div>
      </div>

      <div className="border bg-card">
        {query.isLoading ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} className="h-9 w-full" />
            ))}
          </div>
        ) : query.isError ? (
          <MemoryErrorState
            title="无法加载记忆记录"
            message={resolveMemoryApiError(query.error)}
            onRetry={() => query.refetch()}
          />
        ) : records.length === 0 ? (
          <MemoryEmptyState
            title="暂无记忆记录"
            description="调整筛选条件，或等待对话产生 L1 原子记忆。"
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>记忆</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>所属</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {records.map((record) => {
                const own = Boolean(currentUserId) && record.context?.userId === currentUserId;
                const canCorrect =
                  own && record.status !== "DELETED" && record.status !== "SUPERSEDED";
                return (
                  <TableRow key={record.id}>
                    <TableCell className="max-w-80 text-xs">
                      <p className="line-clamp-2">{record.content}</p>
                      <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">
                        #{record.id} · {record.recordId}
                      </p>
                    </TableCell>
                    <TableCell className="text-xs">
                      {MEMORY_TYPE_LABELS[record.type] ?? record.type}
                      <span className="ms-1 text-muted-foreground">v{record.version}</span>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      <p>{record.context?.userId ?? "—"}</p>
                      <p className="text-[11px]">
                        {record.context?.teamId ?? "—"} · {record.context?.agentId ?? "—"}
                      </p>
                    </TableCell>
                    <TableCell>
                      <Badge variant={record.status === "ACTIVE" ? "secondary" : "outline"}>
                        {MEMORY_RECORD_STATUS_LABELS[record.status as MemoryRecordStatus] ??
                          record.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatUserDateTime(memoryRecordTimestamp(record))}
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            aria-label="记忆记录操作"
                          >
                            <Ellipsis />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-44">
                          <DropdownMenuItem onSelect={() => setDetailId(record.id)}>
                            查看详情
                          </DropdownMenuItem>
                          {canExecute && record.status === "ACTIVE" ? (
                            <DropdownMenuItem
                              onSelect={() => setConfirming({ record, action: "freeze" })}
                            >
                              <Snowflake />
                              冻结
                            </DropdownMenuItem>
                          ) : null}
                          {canExecute && record.status === "DELETED" ? (
                            <DropdownMenuItem
                              onSelect={() => setConfirming({ record, action: "restore" })}
                            >
                              <RotateCcw />
                              恢复
                            </DropdownMenuItem>
                          ) : null}
                          {canCorrect ? (
                            <DropdownMenuItem onSelect={() => setCorrectionTarget(record)}>
                              申请修正
                            </DropdownMenuItem>
                          ) : null}
                          {canDelete ? (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem
                                variant="destructive"
                                onSelect={() => setDeleteTarget(record)}
                              >
                                <Trash2 />
                                删除
                              </DropdownMenuItem>
                            </>
                          ) : null}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
        <div className="border-t px-4 py-2 text-[11px] text-muted-foreground">
          仅展示最近 {MEMORY_REVIEW_PAGE_SIZE} 条记录，使用筛选条件缩小范围。
        </div>
      </div>

      {confirming && confirmCopy ? (
        <MemoryConfirmDialog
          title={confirmCopy.title}
          description={confirmCopy.description}
          confirmLabel={confirmCopy.label}
          pending={actionMutation.isPending}
          onConfirm={() =>
            actionMutation.mutate({ record: confirming.record, action: confirming.action })
          }
          onClose={() => setConfirming(null)}
        />
      ) : null}

      {deleteTarget ? (
        <MemoryRecordDeleteDialog
          record={deleteTarget}
          pending={deleteMutation.isPending}
          onConfirm={(reason) => deleteMutation.mutate({ record: deleteTarget, reason })}
          onClose={() => setDeleteTarget(null)}
        />
      ) : null}

      {correctionTarget ? (
        <MemoryCorrectionDialog
          record={correctionTarget}
          pending={correctionMutation.isPending}
          onSubmit={(request) => correctionMutation.mutate({ record: correctionTarget, request })}
          onClose={() => setCorrectionTarget(null)}
        />
      ) : null}

      {detailId !== null ? (
        <MemoryRecordDetailDialog recordId={detailId} onClose={() => setDetailId(null)} />
      ) : null}

      {rebuildOpen ? (
        <MemoryRebuildDialog
          pending={rebuildMutation.isPending}
          onSubmit={(input) => rebuildMutation.mutate(input)}
          onClose={() => setRebuildOpen(false)}
        />
      ) : null}

      {flushOpen ? (
        <MemoryFlushDialog
          pending={flushMutation.isPending}
          onSubmit={(conversationId) => flushMutation.mutate(conversationId)}
          onClose={() => setFlushOpen(false)}
        />
      ) : null}
    </div>
  );
}

function PipelineCount({ label, value }: { label: string; value: number }) {
  return (
    <span className="flex items-baseline gap-1 text-muted-foreground">
      {label}
      <span className="text-sm font-medium text-foreground tabular-nums">{value}</span>
    </span>
  );
}
