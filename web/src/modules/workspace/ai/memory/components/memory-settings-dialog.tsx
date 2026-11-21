import { useState } from "react";

import { Loader2 } from "lucide-react";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";

import type { MemorySettingItem } from "../lib/api";
import { MemoryErrorState } from "./memory-states";

type MemorySettingsDialogProps = {
  setting?: MemorySettingItem;
  loading: boolean;
  errorMessage?: string;
  pending: boolean;
  onRetry: () => void;
  onSave: (enabled: boolean) => void;
  onClose: () => void;
};

export function MemorySettingsDialog({
  setting,
  loading,
  errorMessage,
  pending,
  onRetry,
  onSave,
  onClose,
}: MemorySettingsDialogProps) {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const currentEnabled = enabled ?? (setting ? setting.spec.memory?.enabled !== false : true);

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="记忆设置"
      description="配置实例级记忆总开关，仅管理员可修改。"
      size="md"
      footer={
        setting ? (
          <>
            <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
              取消
            </Button>
            <Button type="button" disabled={pending} onClick={() => onSave(currentEnabled)}>
              {pending ? <Loader2 className="animate-spin" /> : null}
              保存设置
            </Button>
          </>
        ) : (
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            关闭
          </Button>
        )
      }
    >
      {loading ? (
        <div className="space-y-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : errorMessage ? (
        <MemoryErrorState title="无法读取设置" message={errorMessage} onRetry={onRetry} />
      ) : setting ? (
        <div className="space-y-4">
          <div className="flex items-start justify-between gap-4 border p-3">
            <div className="space-y-1">
              <Label htmlFor="memory-enabled">启用记忆</Label>
              <p className="text-xs text-muted-foreground">
                关闭后聊天不再召回或抽取记忆，记忆页显示停用提示。
              </p>
            </div>
            <Switch
              id="memory-enabled"
              checked={currentEnabled}
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
        </div>
      ) : null}
    </DialogFormShell>
  );
}
