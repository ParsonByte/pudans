"use client";

import type { ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";
import { CircleAlert } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatDuration, requestStatusLabel } from "@/modules/workspace/ai/routing/lib/model";
import { billingStatusLabel, usageSourceLabel } from "@/modules/workspace/ai/usage/lib/model";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import {
  type AiGenerationItem,
  aiGenerationDetailQueryKey,
  fetchAiGeneration,
  resolveAiGenerationApiError,
} from "../lib/api";
import {
  generationCapabilityLabel,
  generationModelTypeLabel,
  generationStatusVariant,
  generationUnitsLabel,
  generationUsageEntries,
} from "../lib/model";
import { GenerationOutputs } from "./generation-outputs";

type GenerationDetailDialogProps = {
  requestId: string;
  onClose: () => void;
};

export function GenerationDetailDialog({ requestId, onClose }: GenerationDetailDialogProps) {
  const detailQuery = useQuery({
    queryKey: aiGenerationDetailQueryKey(requestId),
    queryFn: () => fetchAiGeneration(requestId),
    retry: false,
  });

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>生成详情</DialogTitle>
          <DialogDescription className="font-mono text-xs break-all">{requestId}</DialogDescription>
        </DialogHeader>

        {detailQuery.isError ? (
          <ConsoleState
            variant="error"
            title="无法加载生成详情"
            description={resolveAiGenerationApiError(detailQuery.error)}
            onRetry={() => void detailQuery.refetch()}
          />
        ) : detailQuery.isPending ? (
          <ConsoleBlockSkeleton rows={6} className="space-y-3" itemClassName="h-10" />
        ) : detailQuery.data ? (
          <GenerationDetailBody generation={detailQuery.data} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function GenerationDetailBody({ generation }: { generation: AiGenerationItem }) {
  const usageEntries = generationUsageEntries(generation.usage);

  return (
    <div className="space-y-5">
      {generation.errorMessage ? (
        <Alert variant="destructive">
          <CircleAlert />
          <AlertTitle>
            生成失败{generation.errorCode ? ` · ${generation.errorCode}` : ""}
          </AlertTitle>
          <AlertDescription className="break-words">{generation.errorMessage}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2">
        <DetailItem
          label="状态"
          value={
            <Badge variant={generationStatusVariant(generation.status)}>
              {requestStatusLabel(generation.status)}
            </Badge>
          }
        />
        <DetailItem label="能力" value={generationCapabilityLabel(generation.capability)} />
        <DetailItem label="模型类型" value={generationModelTypeLabel(generation.modelType)} />
        <DetailItem label="供应商" value={generation.provider || "—"} mono />
        <DetailItem label="账户" value={generation.account || "—"} mono />
        <DetailItem label="模型" value={generation.model || "—"} mono />
        <DetailItem label="标准 Token" value={generationUnitsLabel(generation.units)} />
        <DetailItem label="计费状态" value={billingStatusLabel(generation.billingStatus)} />
        <DetailItem label="计费来源" value={usageSourceLabel(generation.billingSource)} />
        <DetailItem label="延迟" value={formatDuration(generation.latencyMs ?? undefined)} />
        <DetailItem label="创建时间" value={formatUserDateTime(generation.createdAt)} />
        <DetailItem
          label="完成时间"
          value={formatUserDateTime(generation.completedAt ?? undefined)}
        />
      </div>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">用量</h3>
        {usageEntries.length === 0 ? (
          <p className="text-xs text-muted-foreground">本次生成没有上报用量。</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {usageEntries.map((entry) => (
              <div key={entry.label} className="border bg-card/40 px-3 py-2">
                <p className="text-[11px] text-muted-foreground">{entry.label}</p>
                <p className="text-sm tabular-nums">{entry.value}</p>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">输出</h3>
        <GenerationOutputs outputs={generation.outputs ?? []} />
      </section>
    </div>
  );
}

type DetailItemProps = {
  label: string;
  value: ReactNode;
  mono?: boolean;
};

function DetailItem({ label, value, mono = false }: DetailItemProps) {
  return (
    <div className="min-w-0 border bg-card/40 px-3 py-2">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <div className={`min-w-0 truncate text-sm ${mono ? "font-mono text-xs" : ""}`}>{value}</div>
    </div>
  );
}
