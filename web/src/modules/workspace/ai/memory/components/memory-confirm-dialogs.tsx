import { useState } from "react";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

import type { MemoryLayerCounts } from "../lib/api";
import { MEMORY_CLEAR_CONFIRM_TEXT } from "../lib/model";

type MemoryConfirmDialogProps = {
  title: string;
  description: string;
  confirmLabel: string;
  pending?: boolean;
  destructive?: boolean;
  onConfirm: () => void;
  onClose: () => void;
};

export function MemoryConfirmDialog({
  title,
  description,
  confirmLabel,
  pending = false,
  destructive = false,
  onConfirm,
  onClose,
}: MemoryConfirmDialogProps) {
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title={title}
      desc={description}
      destructive={destructive}
      isLoading={pending}
      confirmText={pending ? "处理中…" : confirmLabel}
      handleConfirm={onConfirm}
    />
  );
}

type MemoryAssetDeleteDialogProps = {
  assetTitle: string;
  counts?: MemoryLayerCounts;
  pending: boolean;
  onConfirm: (clear: boolean) => void;
  onClose: () => void;
};

export function MemoryAssetDeleteDialog({
  assetTitle,
  counts,
  pending,
  onConfirm,
  onClose,
}: MemoryAssetDeleteDialogProps) {
  const [clear, setClear] = useState(false);

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="删除记忆资产"
      desc={`将从资产列表移除「${assetTitle}」。默认仅删除资产登记，底层记忆数据保留。`}
      destructive
      isLoading={pending}
      confirmText="删除资产"
      handleConfirm={() => onConfirm(clear)}
    >
      <div className="space-y-2">
        <div className="flex items-start gap-2">
          <Checkbox
            id="memory-asset-delete-clear"
            checked={clear}
            onCheckedChange={(checked) => setClear(checked === true)}
            disabled={pending}
          />
          <div className="space-y-0.5">
            <Label htmlFor="memory-asset-delete-clear">同时清空底层记忆内容</Label>
            <p className="text-xs text-muted-foreground">
              {clear
                ? counts
                  ? `将永久删除 L0 ${counts.L0} 条、L1 ${counts.L1} 条、L2 ${counts.L2} 个场景与 L3 画像。`
                  : "将永久删除该作用域内的 L0–L3 全部记忆内容，此操作不可恢复。"
                : "底层 L0–L3 数据会保留，可用同名作用域重新登记资产。"}
            </p>
          </div>
        </div>
      </div>
    </ConfirmDialog>
  );
}

type MemoryClearDialogProps = {
  assetTitle: string;
  counts?: MemoryLayerCounts;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
};

export function MemoryClearDialog({
  assetTitle,
  counts,
  pending,
  onConfirm,
  onClose,
}: MemoryClearDialogProps) {
  const [confirmation, setConfirmation] = useState("");
  const confirmed = confirmation.trim() === MEMORY_CLEAR_CONFIRM_TEXT;

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="清空记忆资产"
      desc={`即将清空「${assetTitle}」的 L0–L3 全部记忆内容，此操作不可恢复。`}
      destructive
      isLoading={pending}
      disabled={!confirmed}
      confirmText="清空记忆"
      handleConfirm={onConfirm}
    >
      <Alert variant="destructive">
        <AlertTitle>影响范围</AlertTitle>
        <AlertDescription>
          {counts
            ? `将删除 L0 ${counts.L0} 条对话、L1 ${counts.L1} 条原子记忆、L2 ${counts.L2} 个场景与 L3 核心画像；已绑定的 Agent 将不再注入这些记忆。`
            : "将删除该记忆块 L0–L3 的全部记忆内容；已绑定的 Agent 将不再注入这些记忆。"}
        </AlertDescription>
      </Alert>
      <div className="space-y-2">
        <Label htmlFor="memory-clear-confirm">输入「{MEMORY_CLEAR_CONFIRM_TEXT}」以确认</Label>
        <Input
          id="memory-clear-confirm"
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
          disabled={pending}
          autoFocus
          placeholder={MEMORY_CLEAR_CONFIRM_TEXT}
        />
      </div>
    </ConfirmDialog>
  );
}
