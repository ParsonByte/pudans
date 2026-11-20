import { useEffect, useMemo, useState } from "react";

import type { ReplayStep } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Pause, Play, RefreshCw, RotateCcw } from "lucide-react";

import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import { formatAuditExactTime } from "@/modules/workspace/console/audit/lib/api";
import { formatAuditSnapshot } from "@/modules/workspace/console/audit/lib/audit-format";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";
import { ConsoleSectionHeader } from "@/modules/workspace/console/components/console-page";

import { fetchReplay } from "../lib/api";
import { ReplayMap } from "./replay-map";

const SPEEDS = ["1", "2", "4"] as const;
const STEP_INTERVAL_MS = 1200;

type ReplayPlayerProps = {
  traceFlowId: string;
};

export function ReplayPlayer({ traceFlowId }: ReplayPlayerProps) {
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState<string>("1");
  const query = useQuery({
    queryKey: ["replay", traceFlowId],
    queryFn: () => fetchReplay(traceFlowId),
    retry: false,
  });
  const steps = useMemo(() => query.data?.steps ?? [], [query.data]);
  const current = steps[index];

  useEffect(() => {
    if (!playing || steps.length === 0) {
      return;
    }
    const timer = window.setTimeout(
      () => {
        setIndex((value) => {
          if (value >= steps.length - 1) {
            setPlaying(false);
            return value;
          }
          return value + 1;
        });
      },
      STEP_INTERVAL_MS / Number(speed),
    );
    return () => window.clearTimeout(timer);
  }, [index, playing, speed, steps.length]);

  if (query.isPending) {
    return (
      <div className="space-y-3 p-5">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (query.isError || steps.length === 0) {
    return (
      <div className="flex min-h-48 flex-col items-center justify-center gap-3 p-6 text-center">
        <p className="text-sm font-medium">无法加载回放步骤</p>
        <p className="text-xs text-muted-foreground">该链路不存在或没有可回放的操作快照。</p>
        <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
          <RefreshCw />
          重试
        </Button>
      </div>
    );
  }

  return (
    <>
      <ConsoleSectionHeader
        title="操作回放"
        description={`链路 ${traceFlowId}`}
        action={
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">
              第 {index + 1} / {steps.length} 步
            </Badge>
            <Select value={speed} onValueChange={setSpeed}>
              <SelectTrigger className="h-8 w-20" aria-label="回放速度">
                <SelectValue />
              </SelectTrigger>
              <SelectContent side="top">
                {SPEEDS.map((item) => (
                  <SelectItem key={item} value={item}>
                    {item}x
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        }
      />
      <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setIndex((value) => Math.max(0, value - 1))}
          disabled={index <= 0}
        >
          <ChevronLeft />
          上一步
        </Button>
        <Button type="button" size="sm" onClick={() => setPlaying((value) => !value)}>
          {playing ? <Pause /> : <Play />}
          {playing ? "暂停" : "播放"}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setIndex((value) => Math.min(steps.length - 1, value + 1))}
          disabled={index >= steps.length - 1}
        >
          下一步
          <ChevronRight />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => {
            setPlaying(false);
            setIndex(0);
          }}
        >
          <RotateCcw />
          重置
        </Button>
      </div>

      <div className="grid gap-4 p-5 xl:grid-cols-[minmax(0,1fr)_24rem]">
        <ReplayMap
          steps={steps}
          activeStep={current}
          onSelectStepIndex={(stepIndex) => {
            setPlaying(false);
            setIndex(stepIndex);
          }}
        />
        <ReplayStepDetail step={current} />
      </div>
    </>
  );
}

function ReplayStepDetail({ step }: { step?: ReplayStep }) {
  if (!step) {
    return null;
  }
  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      <div className="grid shrink-0 gap-4 border p-4 sm:grid-cols-2">
        <InfoRow label="时间" value={formatAuditExactTime(step.timestamp)} />
        <InfoRow label="结果" value={<AuditResultBadge result={step.result} />} />
        <InfoRow label="操作" value={auditOperationLabel(step.operation)} />
        <InfoRow label="用户 UID" value={step.uid || "—"} />
        <InfoRow label="资源" value={auditResourceLabel(step.resourceType)} />
        <InfoRow label="资源名称" value={step.resourceName || "—"} />
        <InfoRow
          label="请求"
          value={
            <span className="font-mono text-xs break-all">
              {step.requestMethod || "—"} {step.requestPath || ""}
            </span>
          }
          className="sm:col-span-2"
        />
        <InfoRow label="耗时" value={step.durationMs ? `${step.durationMs} ms` : "—"} />
        <InfoRow
          label="归档"
          value={step.archived ? <Badge variant="outline">已归档</Badge> : "活跃"}
        />
        <InfoRow
          label="审计 ID"
          value={<span className="font-mono text-xs break-all">{step.auditId || "—"}</span>}
          className="sm:col-span-2"
        />
      </div>
      <section className="flex min-h-0 flex-1 flex-col gap-2">
        <h3 className="text-sm font-semibold">操作快照</h3>
        <pre className="min-h-40 flex-1 overflow-auto border bg-muted/40 p-3 text-xs leading-5 break-all whitespace-pre-wrap">
          {formatAuditSnapshot(step.actionSnapshot)}
        </pre>
      </section>
    </div>
  );
}
