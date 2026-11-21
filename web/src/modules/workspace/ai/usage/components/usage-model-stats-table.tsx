"use client";

import { useMemo } from "react";

import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type {
  AiModelCapability,
  AiModelItem,
  AiModelType,
} from "@/modules/workspace/ai/providers/lib/api";
import {
  MODEL_CAPABILITY_OPTIONS,
  MODEL_TYPE_OPTIONS,
  formatNumber,
  modelDisplayName,
  optionLabel,
} from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount, requestStatusLabel } from "@/modules/workspace/ai/routing/lib/model";

import type { ModelStatsRow } from "../lib/api";

type UsageModelStatsTableProps = {
  rows: ModelStatsRow[];
  models?: AiModelItem[];
};

export function UsageModelStatsTable({ rows, models = [] }: UsageModelStatsTableProps) {
  const modelNames = useMemo(
    () => new Map(models.map((model) => [model.metadata.name, modelDisplayName(model)] as const)),
    [models],
  );
  const sorted = useMemo(
    () =>
      [...rows].sort((left, right) => right.units - left.units || right.requests - left.requests),
    [rows],
  );

  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="px-5">模型</TableHead>
            <TableHead className="px-5">类型</TableHead>
            <TableHead className="px-5">能力</TableHead>
            <TableHead className="px-5">状态</TableHead>
            <TableHead className="px-5 text-end">请求</TableHead>
            <TableHead className="px-5 text-end">标准 Token</TableHead>
            <TableHead className="px-5">媒体产出</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((row) => (
            <TableRow key={`${row.modelType}-${row.capability}-${row.model}-${row.status}`}>
              <TableCell className="px-5 py-2.5 font-mono text-xs">
                {modelNames.get(row.model) ?? row.model}
              </TableCell>
              <TableCell className="px-5 py-2.5">
                <Badge variant="outline">
                  {optionLabel(MODEL_TYPE_OPTIONS, row.modelType as AiModelType, row.modelType)}
                </Badge>
              </TableCell>
              <TableCell className="px-5 py-2.5 text-xs text-muted-foreground">
                {optionLabel(
                  MODEL_CAPABILITY_OPTIONS,
                  row.capability as AiModelCapability,
                  row.capability,
                )}
              </TableCell>
              <TableCell className="px-5 py-2.5">
                <Badge variant={row.status === "FAILED" ? "destructive" : "secondary"}>
                  {requestStatusLabel(row.status)}
                </Badge>
              </TableCell>
              <TableCell className="px-5 py-2.5 text-end text-sm tabular-nums">
                {formatNumber(row.requests)}
              </TableCell>
              <TableCell className="px-5 py-2.5 text-end text-sm tabular-nums">
                {formatTokenCount(row.units)}
              </TableCell>
              <TableCell className="px-5 py-2.5 text-xs text-muted-foreground">
                {mediaOutput(row)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function mediaOutput(row: ModelStatsRow): string {
  const parts: string[] = [];
  if (row.images > 0) {
    parts.push(`图片 ${formatNumber(row.images)}`);
  }
  if (row.videoSeconds > 0) {
    parts.push(`视频 ${formatNumber(row.videoSeconds)} 秒`);
  }
  if (row.audioSeconds > 0) {
    parts.push(`音频 ${formatNumber(row.audioSeconds)} 秒`);
  }
  if (row.speechCharacters > 0) {
    parts.push(`语音 ${formatNumber(row.speechCharacters)} 字`);
  }
  if (row.tasks > 0) {
    parts.push(`任务 ${formatNumber(row.tasks)}`);
  }
  return parts.join(" · ") || "—";
}
