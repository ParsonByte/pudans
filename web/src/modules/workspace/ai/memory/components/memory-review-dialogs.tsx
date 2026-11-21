import { useState } from "react";

import { Loader2 } from "lucide-react";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type {
  MemoryCorrectionInput,
  MemoryRebuildInput,
  MemoryRebuildLevel,
  MemoryRecordItem,
  MemoryRequestItem,
} from "../lib/api";
import { resolveMemoryApiError } from "../lib/api";
import {
  MEMORY_REBUILD_LEVEL_OPTIONS,
  MEMORY_TYPE_LABELS,
  memoryRecordTimestamp,
} from "../lib/model";
import { useMemoryRecord } from "../lib/queries";
import { MemoryDetailRow } from "./memory-detail-row";
import { MemoryErrorState } from "./memory-states";

type MemoryRequestReviewDialogProps = {
  request: MemoryRequestItem;
  mode: "approve" | "reject";
  pending: boolean;
  onConfirm: (comment: string) => void;
  onClose: () => void;
};

export function MemoryRequestReviewDialog({
  request,
  mode,
  pending,
  onConfirm,
  onClose,
}: MemoryRequestReviewDialogProps) {
  const [comment, setComment] = useState("");
  const trimmed = comment.trim();
  const disabled = mode === "reject" && !trimmed;

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title={mode === "approve" ? "通过记忆申请" : "驳回记忆申请"}
      desc={
        mode === "approve"
          ? `将按申请内容处理记忆 #${request.memoryId}，并记录审核意见。`
          : `将驳回记忆 #${request.memoryId} 的申请，驳回意见必填。`
      }
      destructive={mode === "reject"}
      disabled={disabled}
      isLoading={pending}
      confirmText={mode === "approve" ? "通过" : "驳回"}
      handleConfirm={() => onConfirm(trimmed)}
    >
      <div className="space-y-2">
        <Label htmlFor="memory-request-review-comment">
          审核意见{mode === "reject" ? "（必填）" : "（可选）"}
        </Label>
        <Textarea
          id="memory-request-review-comment"
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          rows={3}
          disabled={pending}
        />
      </div>
    </ConfirmDialog>
  );
}

type MemoryRecordDeleteDialogProps = {
  record: MemoryRecordItem;
  pending: boolean;
  onConfirm: (reason: string) => void;
  onClose: () => void;
};

export function MemoryRecordDeleteDialog({
  record,
  pending,
  onConfirm,
  onClose,
}: MemoryRecordDeleteDialogProps) {
  const [reason, setReason] = useState("");
  const trimmed = reason.trim();

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="删除记忆记录"
      desc={`将逻辑删除记忆 #${record.id}，系统会保存删除原因与快照，可通过恢复操作还原。`}
      destructive
      disabled={!trimmed}
      isLoading={pending}
      confirmText="删除"
      handleConfirm={() => onConfirm(trimmed)}
    >
      <div className="space-y-2">
        <Label htmlFor="memory-record-delete-reason">删除原因（必填）</Label>
        <Textarea
          id="memory-record-delete-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          rows={3}
          disabled={pending}
        />
      </div>
    </ConfirmDialog>
  );
}

type MemoryCorrectionDialogProps = {
  record: MemoryRecordItem;
  pending: boolean;
  onSubmit: (input: MemoryCorrectionInput) => void;
  onClose: () => void;
};

export function MemoryCorrectionDialog({
  record,
  pending,
  onSubmit,
  onClose,
}: MemoryCorrectionDialogProps) {
  const [requestType, setRequestType] = useState<MemoryCorrectionInput["requestType"]>("CORRECT");
  const [reason, setReason] = useState("");
  const [suggestedContent, setSuggestedContent] = useState("");
  const valid =
    reason.trim().length > 0 && (requestType === "DELETE" || suggestedContent.trim().length > 0);

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>提交记忆修正申请</DialogTitle>
          <DialogDescription>
            针对记忆 #{record.id} 提交更正或删除申请，审核通过后才会应用到记忆。
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label>申请类型</Label>
            <ToggleGroup
              type="single"
              variant="outline"
              size="sm"
              value={requestType}
              onValueChange={(value) => {
                if (value === "DELETE" || value === "CORRECT") {
                  setRequestType(value);
                }
              }}
              disabled={pending}
            >
              <ToggleGroupItem value="CORRECT">更正内容</ToggleGroupItem>
              <ToggleGroupItem value="DELETE">删除记忆</ToggleGroupItem>
            </ToggleGroup>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="memory-correction-reason">申请原因（必填）</Label>
            <Textarea
              id="memory-correction-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
              disabled={pending}
            />
          </div>
          {requestType === "CORRECT" ? (
            <div className="space-y-1.5">
              <Label htmlFor="memory-correction-content">建议内容（必填）</Label>
              <Textarea
                id="memory-correction-content"
                value={suggestedContent}
                onChange={(event) => setSuggestedContent(event.target.value)}
                rows={5}
                disabled={pending}
              />
            </div>
          ) : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button
            type="button"
            disabled={!valid || pending}
            onClick={() =>
              onSubmit({
                requestType,
                reason: reason.trim(),
                suggestedContent: requestType === "CORRECT" ? suggestedContent.trim() : undefined,
              })
            }
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            提交申请
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type MemoryRebuildDialogProps = {
  pending: boolean;
  onSubmit: (input: MemoryRebuildInput) => void;
  onClose: () => void;
};

export function MemoryRebuildDialog({ pending, onSubmit, onClose }: MemoryRebuildDialogProps) {
  const [conversationId, setConversationId] = useState("");
  const [level, setLevel] = useState<MemoryRebuildLevel>("L2");
  const [teamId, setTeamId] = useState("");
  const [agentId, setAgentId] = useState("");
  const [afterL1Id, setAfterL1Id] = useState("");
  const parsedConversationId = Number(conversationId.trim());
  const valid = Number.isSafeInteger(parsedConversationId) && parsedConversationId > 0;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>重建记忆</DialogTitle>
          <DialogDescription>
            为指定会话重新排队 L1 抽取，或重建 L2 / L3 聚合结果。
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">会话 ID（必填）</span>
              <Input
                value={conversationId}
                onChange={(event) => setConversationId(event.target.value)}
                inputMode="numeric"
                disabled={pending}
              />
            </label>
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">重建层级</span>
              <Select
                value={level}
                onValueChange={(value) => setLevel(value as MemoryRebuildLevel)}
                disabled={pending}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MEMORY_REBUILD_LEVEL_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          </div>
          {level !== "L1" ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="space-y-1.5 text-sm">
                <span className="text-muted-foreground">团队 ID（留空取会话团队）</span>
                <Input
                  value={teamId}
                  onChange={(event) => setTeamId(event.target.value)}
                  disabled={pending}
                />
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="text-muted-foreground">Agent ID（留空取会话 Agent）</span>
                <Input
                  value={agentId}
                  onChange={(event) => setAgentId(event.target.value)}
                  disabled={pending}
                />
              </label>
            </div>
          ) : null}
          {level !== "L1" ? (
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">从 L1 ID 之后开始（可空）</span>
              <Input
                value={afterL1Id}
                onChange={(event) => setAfterL1Id(event.target.value)}
                inputMode="numeric"
                disabled={pending}
              />
            </label>
          ) : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button
            type="button"
            disabled={!valid || pending}
            onClick={() =>
              onSubmit({
                conversationId: parsedConversationId,
                level,
                teamId: teamId.trim() || undefined,
                agentId: agentId.trim() || undefined,
                afterL1Id: afterL1Id.trim() ? Number(afterL1Id.trim()) : undefined,
              })
            }
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            排队重建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type MemoryFlushDialogProps = {
  pending: boolean;
  onSubmit: (conversationId: number) => void;
  onClose: () => void;
};

export function MemoryFlushDialog({ pending, onSubmit, onClose }: MemoryFlushDialogProps) {
  const [conversationId, setConversationId] = useState("");
  const parsed = Number(conversationId.trim());
  const valid = Number.isSafeInteger(parsed) && parsed > 0;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>手动刷新记忆流水线</DialogTitle>
          <DialogDescription>立即为指定会话的最后一条消息排队 L1 抽取任务。</DialogDescription>
        </DialogHeader>
        <label className="space-y-1.5 text-sm">
          <span className="text-muted-foreground">会话 ID</span>
          <Input
            value={conversationId}
            onChange={(event) => setConversationId(event.target.value)}
            inputMode="numeric"
            disabled={pending}
          />
        </label>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button type="button" disabled={!valid || pending} onClick={() => onSubmit(parsed)}>
            {pending ? <Loader2 className="animate-spin" /> : null}
            排队抽取
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type MemoryRecordDetailDialogProps = {
  recordId: number;
  onClose: () => void;
};

export function MemoryRecordDetailDialog({ recordId, onClose }: MemoryRecordDetailDialogProps) {
  const query = useMemoryRecord(String(recordId), true);
  const record = query.data;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>记忆记录 #{recordId}</DialogTitle>
          <DialogDescription>读取 L1 原子记忆上下文、状态与完整正文。</DialogDescription>
        </DialogHeader>
        {query.isLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} className="h-5 w-full" />
            ))}
          </div>
        ) : query.isError ? (
          <MemoryErrorState
            message={resolveMemoryApiError(query.error)}
            onRetry={() => void query.refetch()}
          />
        ) : record ? (
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-1.5">
              <Badge variant="outline">{MEMORY_TYPE_LABELS[record.type] ?? record.type}</Badge>
              <Badge variant="secondary">{record.status}</Badge>
              <Badge variant="outline">v{record.version}</Badge>
              {record.scope ? <Badge variant="outline">{record.scope}</Badge> : null}
            </div>
            <div className="grid gap-2 text-xs sm:grid-cols-2">
              <MemoryDetailRow label="用户">{record.context?.userId ?? "—"}</MemoryDetailRow>
              <MemoryDetailRow label="团队">{record.context?.teamId ?? "—"}</MemoryDetailRow>
              <MemoryDetailRow label="Agent">{record.context?.agentId ?? "—"}</MemoryDetailRow>
              <MemoryDetailRow label="场景">{record.sceneName || "—"}</MemoryDetailRow>
              <MemoryDetailRow label="优先级">{String(record.priority)}</MemoryDetailRow>
              <MemoryDetailRow label="会话">{record.conversationId ?? "—"}</MemoryDetailRow>
              <MemoryDetailRow label="更新时间">
                {formatUserDateTime(memoryRecordTimestamp(record))}
              </MemoryDetailRow>
              <MemoryDetailRow label="记录 ID">{record.recordId}</MemoryDetailRow>
            </div>
            <div className="space-y-1">
              <span className="text-xs text-muted-foreground">正文</span>
              <div className="max-h-72 overflow-y-auto border bg-muted/40 p-3">
                <pre className="font-mono text-xs leading-relaxed whitespace-pre-wrap">
                  {record.content}
                </pre>
              </div>
            </div>
          </div>
        ) : null}
        <DialogFooter>
          <Button type="button" onClick={onClose}>
            关闭
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
