import { useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Eye, RefreshCw, ShieldCheck, ShieldX } from "lucide-react";
import { toast } from "sonner";

import {
  type ContentPreview,
  ContentPreviewButton,
  ContentPreviewDialog,
} from "@/components/content/content-preview";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import {
  type MemoryRequestItem,
  type MemoryRequestStatus,
  type OrgMemoryItem,
  approveMemoryRequest,
  approveOrgMemory,
  rejectMemoryRequest,
  rejectOrgMemory,
  resolveMemoryApiError,
  revokeOrgMemory,
} from "../lib/api";
import {
  MEMORY_REQUEST_STATUS_LABELS,
  MEMORY_REQUEST_TYPE_LABELS,
  MEMORY_REVIEW_PAGE_SIZE,
} from "../lib/model";
import { invalidateMemoryReviewQueries, useMemoryRequests, useOrgMemories } from "../lib/queries";
import { MemoryConfirmDialog } from "./memory-confirm-dialogs";
import { MemoryRequestReviewDialog } from "./memory-review-dialogs";
import { MemoryEmptyState, MemoryErrorState } from "./memory-states";

type ReviewActionProps = {
  canReview: boolean;
};

const REQUEST_STATUS_OPTIONS: readonly { value: "ALL" | MemoryRequestStatus; label: string }[] = [
  { value: "ALL", label: "全部" },
  { value: "PENDING", label: "待审核" },
  { value: "APPROVED", label: "已通过" },
  { value: "REJECTED", label: "已驳回" },
];

export function MemoryRequestsPanel({ canReview }: ReviewActionProps) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<"ALL" | MemoryRequestStatus>("PENDING");
  const [reviewing, setReviewing] = useState<{
    request: MemoryRequestItem;
    mode: "approve" | "reject";
  } | null>(null);
  const [preview, setPreview] = useState<ContentPreview | null>(null);

  const query = useMemoryRequests(
    {
      status: status === "ALL" ? undefined : status,
      limit: MEMORY_REVIEW_PAGE_SIZE,
    },
    true,
  );

  const reviewMutation = useMutation({
    mutationFn: async (input: {
      request: MemoryRequestItem;
      mode: "approve" | "reject";
      comment: string;
    }) =>
      input.mode === "approve"
        ? approveMemoryRequest(input.request.id, input.comment)
        : rejectMemoryRequest(input.request.id, input.comment),
    onSuccess: async (_, input) => {
      await invalidateMemoryReviewQueries(queryClient);
      toast.success(input.mode === "approve" ? "记忆申请已通过" : "记忆申请已驳回");
      setReviewing(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const requests = query.data ?? [];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup
          type="single"
          variant="outline"
          size="sm"
          value={status}
          onValueChange={(value) => {
            if (value) {
              setStatus(value as "ALL" | MemoryRequestStatus);
            }
          }}
        >
          {REQUEST_STATUS_OPTIONS.map((option) => (
            <ToggleGroupItem key={option.value} value={option.value}>
              {option.label}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        <span className="text-xs text-muted-foreground">
          仅列出最近 {MEMORY_REVIEW_PAGE_SIZE} 条申请
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="ms-auto"
          disabled={query.isFetching}
          onClick={() => query.refetch()}
        >
          <RefreshCw className={query.isFetching ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      <div className="border bg-card">
        {query.isLoading ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-9 w-full" />
            ))}
          </div>
        ) : query.isError ? (
          <MemoryErrorState
            title="无法加载记忆申请"
            message={resolveMemoryApiError(query.error)}
            onRetry={() => query.refetch()}
          />
        ) : requests.length === 0 ? (
          <MemoryEmptyState
            title="暂无记忆申请"
            description="用户对自身记忆提交的更正或删除申请会显示在这里。"
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>申请</TableHead>
                <TableHead>记忆</TableHead>
                <TableHead>申请人</TableHead>
                <TableHead>原因</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>提交时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {requests.map((request) => (
                <TableRow key={request.id}>
                  <TableCell className="text-xs">
                    <Badge variant="outline">
                      {MEMORY_REQUEST_TYPE_LABELS[request.requestType as "DELETE" | "CORRECT"] ??
                        request.requestType}
                    </Badge>
                    <span className="ms-1.5 text-muted-foreground">#{request.id}</span>
                  </TableCell>
                  <TableCell className="font-mono text-xs">#{request.memoryId}</TableCell>
                  <TableCell className="text-xs">
                    <p>{request.userId}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {request.teamId ?? "—"} · {request.agentId ?? "—"}
                    </p>
                  </TableCell>
                  <TableCell className="max-w-56 text-xs text-muted-foreground">
                    <span className="line-clamp-2">{request.reason}</span>
                  </TableCell>
                  <TableCell>
                    <Badge variant={request.status === "PENDING" ? "secondary" : "outline"}>
                      {MEMORY_REQUEST_STATUS_LABELS[request.status as MemoryRequestStatus] ??
                        request.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatUserDateTime(request.createdAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <ContentPreviewButton
                        label="查看申请详情"
                        onClick={() =>
                          setPreview({
                            title: `记忆申请 #${request.id}`,
                            description: `${
                              MEMORY_REQUEST_TYPE_LABELS[
                                request.requestType as "DELETE" | "CORRECT"
                              ] ?? request.requestType
                            } · 记忆 #${request.memoryId}`,
                            content: [
                              `**原因**\n\n${request.reason}`,
                              request.suggestedContent
                                ? `**建议内容**\n\n${request.suggestedContent}`
                                : "",
                              request.reviewedBy
                                ? `**审核**\n\n${request.reviewedBy} · ${request.reviewComment ?? "无意见"}`
                                : "",
                            ]
                              .filter(Boolean)
                              .join("\n\n"),
                          })
                        }
                      />
                      {request.status === "PENDING" && canReview ? (
                        <>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={reviewMutation.isPending}
                            onClick={() => setReviewing({ request, mode: "approve" })}
                          >
                            <ShieldCheck />
                            通过
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={reviewMutation.isPending}
                            onClick={() => setReviewing({ request, mode: "reject" })}
                          >
                            <ShieldX />
                            驳回
                          </Button>
                        </>
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <ContentPreviewDialog preview={preview} onClose={() => setPreview(null)} />

      {reviewing ? (
        <MemoryRequestReviewDialog
          request={reviewing.request}
          mode={reviewing.mode}
          pending={reviewMutation.isPending}
          onConfirm={(comment) =>
            reviewMutation.mutate({ request: reviewing.request, mode: reviewing.mode, comment })
          }
          onClose={() => setReviewing(null)}
        />
      ) : null}
    </div>
  );
}

export function OrgMemoriesPanel({ canReview }: ReviewActionProps) {
  const queryClient = useQueryClient();
  const [trust, setTrust] = useState<"pending" | "verified">("pending");
  const [action, setAction] = useState<{
    memory: OrgMemoryItem;
    mode: "approve" | "reject" | "revoke";
  } | null>(null);
  const [preview, setPreview] = useState<ContentPreview | null>(null);

  const query = useOrgMemories(trust, { limit: MEMORY_REVIEW_PAGE_SIZE }, true);

  const mutation = useMutation({
    mutationFn: async (input: { memory: OrgMemoryItem; mode: "approve" | "reject" | "revoke" }) => {
      if (input.mode === "approve") {
        return approveOrgMemory(input.memory.id);
      }
      if (input.mode === "reject") {
        return rejectOrgMemory(input.memory.id);
      }
      return revokeOrgMemory(input.memory.id);
    },
    onSuccess: async (_, input) => {
      await invalidateMemoryReviewQueries(queryClient);
      toast.success(
        input.mode === "approve"
          ? "组织记忆已通过"
          : input.mode === "reject"
            ? "组织记忆已驳回"
            : "组织记忆已撤销",
      );
      setAction(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const items = query.data ?? [];
  const actionCopy = action
    ? action.mode === "approve"
      ? {
          title: "通过组织记忆",
          description: `通过后「${action.memory.content.slice(0, 60)}」将进入组织记忆，可被团队检索。`,
          confirmLabel: "通过",
          destructive: false,
        }
      : action.mode === "reject"
        ? {
            title: "驳回组织记忆",
            description: `将拒绝该组织记忆候选并停止参与跨团队复用。`,
            confirmLabel: "驳回",
            destructive: true,
          }
        : {
            title: "撤销组织记忆",
            description: `撤销后该组织记忆不再对任何团队生效，来源血缘仍会保留。`,
            confirmLabel: "撤销",
            destructive: true,
          }
    : null;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup
          type="single"
          variant="outline"
          size="sm"
          value={trust}
          onValueChange={(value) => {
            if (value === "pending" || value === "verified") {
              setTrust(value);
            }
          }}
        >
          <ToggleGroupItem value="pending">待审核</ToggleGroupItem>
          <ToggleGroupItem value="verified">已通过</ToggleGroupItem>
        </ToggleGroup>
        <span className="text-xs text-muted-foreground">
          {trust === "pending"
            ? "跨团队候选等待审核，通过后才会进入组织记忆。"
            : "已生效的组织记忆，可按需撤销。"}
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="ms-auto"
          disabled={query.isFetching}
          onClick={() => query.refetch()}
        >
          <RefreshCw className={query.isFetching ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      <div className="border bg-card">
        {query.isLoading ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-9 w-full" />
            ))}
          </div>
        ) : query.isError ? (
          <MemoryErrorState
            title="无法加载组织记忆"
            message={resolveMemoryApiError(query.error)}
            onRetry={() => query.refetch()}
          />
        ) : items.length === 0 ? (
          <MemoryEmptyState
            title={trust === "pending" ? "暂无待审核组织记忆" : "暂无已通过组织记忆"}
            description={
              trust === "pending"
                ? "多个团队出现相似记忆后会生成组织记忆候选。"
                : "通过审核的组织记忆会显示在这里。"
            }
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>内容</TableHead>
                <TableHead>来源</TableHead>
                <TableHead>投票</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((memory) => (
                <TableRow key={memory.id}>
                  <TableCell className="max-w-72 text-xs">
                    <span className="line-clamp-2">{memory.content}</span>
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    <p>团队 {memory.sourceTeamIds.join("、") || "—"}</p>
                    <p>用户 {memory.sourceUserIds.join("、") || "—"}</p>
                  </TableCell>
                  <TableCell className="text-sm tabular-nums">{memory.crossVoteCount}</TableCell>
                  <TableCell>
                    <Badge variant={memory.active ? "secondary" : "outline"}>
                      {memory.trustLevel}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatUserDateTime(memory.updatedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label="查看组织记忆"
                        onClick={() =>
                          setPreview({
                            title: `组织记忆 #${memory.id}`,
                            description: `${memory.trustLevel} · 投票 ${memory.crossVoteCount}`,
                            content: memory.content,
                          })
                        }
                      >
                        <Eye />
                      </Button>
                      {canReview ? (
                        trust === "pending" ? (
                          <>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              disabled={mutation.isPending}
                              onClick={() => setAction({ memory, mode: "approve" })}
                            >
                              通过
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              disabled={mutation.isPending}
                              onClick={() => setAction({ memory, mode: "reject" })}
                            >
                              驳回
                            </Button>
                          </>
                        ) : (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={mutation.isPending}
                            onClick={() => setAction({ memory, mode: "revoke" })}
                          >
                            撤销
                          </Button>
                        )
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <ContentPreviewDialog preview={preview} onClose={() => setPreview(null)} />

      {action && actionCopy ? (
        <MemoryConfirmDialog
          title={actionCopy.title}
          description={actionCopy.description}
          confirmLabel={actionCopy.confirmLabel}
          destructive={actionCopy.destructive}
          pending={mutation.isPending}
          onConfirm={() => mutation.mutate(action)}
          onClose={() => setAction(null)}
        />
      ) : null}
    </div>
  );
}
