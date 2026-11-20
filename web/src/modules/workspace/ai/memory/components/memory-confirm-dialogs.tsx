import { useState } from "react";

import { Loader2 } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
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
    <AlertDialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending} onClick={onClose}>
            取消
          </AlertDialogCancel>
          <AlertDialogAction
            variant={destructive ? "destructive" : "default"}
            disabled={pending}
            onClick={(event) => {
              event.preventDefault();
              onConfirm();
            }}
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
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
          <DialogTitle>清空记忆资产</DialogTitle>
          <DialogDescription>
            即将清空「{assetTitle}」的 L0–L3 全部记忆内容，此操作不可恢复。
          </DialogDescription>
        </DialogHeader>
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
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={!confirmed || pending}
            onClick={onConfirm}
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            清空记忆
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
