import { useState } from "react";

import { Loader2 } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";

import type { MemorySettingItem } from "../lib/api";

type MemorySettingsDialogProps = {
  setting?: MemorySettingItem;
  loading: boolean;
  errorMessage?: string;
  pending: boolean;
  onRetry: () => void;
  onSave: (enabled: boolean) => void;
  onClose: () => void;
};

function MemorySettingsForm({
  setting,
  pending,
  onSave,
  onClose,
}: {
  setting: MemorySettingItem;
  pending: boolean;
  onSave: (enabled: boolean) => void;
  onClose: () => void;
}) {
  const [enabled, setEnabled] = useState(setting.spec.memory?.enabled !== false);

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
        <div className="space-y-1">
          <Label htmlFor="memory-enabled">启用记忆</Label>
          <p className="text-xs text-muted-foreground">
            关闭后聊天不再召回或抽取记忆，记忆页显示停用提示。
          </p>
        </div>
        <Switch
          id="memory-enabled"
          checked={enabled}
          onCheckedChange={setEnabled}
          disabled={pending}
        />
      </div>
      <Alert>
        <AlertTitle>管理员设置</AlertTitle>
        <AlertDescription>
          该开关写入 aisettings 的 memory.enabled，对所有团队与 Agent 生效。
        </AlertDescription>
      </Alert>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
          取消
        </Button>
        <Button type="button" disabled={pending} onClick={() => onSave(enabled)}>
          {pending ? <Loader2 className="animate-spin" /> : null}
          保存设置
        </Button>
      </DialogFooter>
    </div>
  );
}

export function MemorySettingsDialog({
  setting,
  loading,
  errorMessage,
  pending,
  onRetry,
  onSave,
  onClose,
}: MemorySettingsDialogProps) {
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
          <DialogTitle>记忆设置</DialogTitle>
          <DialogDescription>配置实例级记忆总开关，仅管理员可修改。</DialogDescription>
        </DialogHeader>
        {loading ? (
          <div className="space-y-2">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : errorMessage ? (
          <div className="space-y-2">
            <Alert variant="destructive">
              <AlertTitle>无法读取设置</AlertTitle>
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
            <Button type="button" variant="outline" size="sm" onClick={onRetry}>
              重试
            </Button>
          </div>
        ) : setting ? (
          <MemorySettingsForm
            key={String(setting.spec.memory?.enabled)}
            setting={setting}
            pending={pending}
            onSave={onSave}
            onClose={onClose}
          />
        ) : null}
        {!setting ? (
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
              关闭
            </Button>
          </DialogFooter>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
