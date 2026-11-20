import type { ReactNode } from "react";

import { Loader2 } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { MemoryGenerationLogItem } from "../lib/api";
import { resolveMemoryApiError } from "../lib/api";
import { MEMORY_LAYER_META, MEMORY_PROMPT_SOURCE_LABELS } from "../lib/model";
import { useMemoryGenerationLog } from "../lib/queries";

function logLayerLabel(layer: string | null | undefined): string {
  return MEMORY_LAYER_META.find((meta) => meta.id === layer)?.label ?? "—";
}

function LogRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[96px_1fr] gap-2 border-b py-2 text-xs last:border-b-0">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words">{children}</dd>
    </div>
  );
}

function GenerationLogDetail({ log }: { log: MemoryGenerationLogItem }) {
  return (
    <dl>
      <LogRow label="日志 ID">{log.id}</LogRow>
      <LogRow label="层级">{logLayerLabel(log.memoryLevel)}</LogRow>
      <LogRow label="记忆 ID">{log.memoryId ?? "—"}</LogRow>
      <LogRow label="团队">{log.teamId ?? "—"}</LogRow>
      <LogRow label="Agent">{log.agentId ?? "—"}</LogRow>
      <LogRow label="用户">{log.userId ?? "—"}</LogRow>
      <LogRow label="会话">{log.sessionId ?? "—"}</LogRow>
      <LogRow label="提示词">{log.promptId ?? "—"}</LogRow>
      <LogRow label="版本">{log.promptVersion ?? "—"}</LogRow>
      <LogRow label="来源">
        {log.promptSource
          ? (MEMORY_PROMPT_SOURCE_LABELS[log.promptSource.toUpperCase()] ?? log.promptSource)
          : "—"}
      </LogRow>
      <LogRow label="提示词哈希">
        <span className="font-mono">{log.promptHash ?? "—"}</span>
      </LogRow>
      <LogRow label="模型">{log.modelCode ?? "—"}</LogRow>
      <LogRow label="输入引用">
        <span className="font-mono">{log.inputRef ?? "—"}</span>
      </LogRow>
      <LogRow label="输出引用">
        <span className="font-mono">{log.outputRef ?? "—"}</span>
      </LogRow>
      <LogRow label="生成时间">{formatUserDateTime(log.createdAt ?? undefined)}</LogRow>
    </dl>
  );
}

type GenerationLogDetailSheetProps = {
  logId: number;
  onClose: () => void;
};

export function GenerationLogDetailSheet({ logId, onClose }: GenerationLogDetailSheetProps) {
  const query = useMemoryGenerationLog(logId);

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle>生成日志详情</SheetTitle>
          <SheetDescription>查看记忆生成过程中的提示词、模型与输入输出引用。</SheetDescription>
        </SheetHeader>
        <div className="px-4 pb-6">
          {query.isLoading ? (
            <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              加载日志…
            </div>
          ) : null}
          {query.isError ? (
            <div className="space-y-3 py-4">
              <Alert variant="destructive">
                <AlertTitle>无法加载生成日志</AlertTitle>
                <AlertDescription>{resolveMemoryApiError(query.error)}</AlertDescription>
              </Alert>
              <Button type="button" variant="outline" size="sm" onClick={() => query.refetch()}>
                重试
              </Button>
            </div>
          ) : null}
          {query.data ? <GenerationLogDetail log={query.data} /> : null}
        </div>
      </SheetContent>
    </Sheet>
  );
}
