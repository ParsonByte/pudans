import { useState } from "react";

import { RefreshCw } from "lucide-react";

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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { MemoryAssetView, MemoryLayer } from "../lib/api";
import { resolveMemoryApiError } from "../lib/api";
import {
  MEMORY_LAYER_META,
  MEMORY_LOG_SOURCE_OPTIONS,
  MEMORY_PROMPT_LAYER_OPTIONS,
  MEMORY_PROMPT_SOURCE_LABELS,
  memoryAssetTitle,
} from "../lib/model";
import { useMemoryGenerationLogs } from "../lib/queries";
import { GenerationLogDetailSheet } from "./generation-log-detail-sheet";
import { MemoryEmptyState, MemoryErrorState } from "./memory-states";

type MemoryLogsTabProps = {
  assets: MemoryAssetView[];
  team: string;
};

export function MemoryLogsTab({ assets, team }: MemoryLogsTabProps) {
  const [assetName, setAssetName] = useState("");
  const [layer, setLayer] = useState<"ALL" | MemoryLayer>("ALL");
  const [source, setSource] = useState("ALL");
  const [logId, setLogId] = useState<number | null>(null);

  const query = useMemoryGenerationLogs(
    assetName || undefined,
    team,
    {
      layer: layer === "ALL" ? undefined : layer,
      status: source === "ALL" ? undefined : source,
      limit: 20,
    },
    Boolean(assetName),
  );

  const items = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Select value={assetName} onValueChange={setAssetName}>
          <SelectTrigger className="w-64" size="sm" aria-label="选择记忆块">
            <SelectValue placeholder="选择记忆块查看日志" />
          </SelectTrigger>
          <SelectContent>
            {assets.map((asset) => (
              <SelectItem key={asset.name} value={asset.name}>
                {memoryAssetTitle(asset)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={layer} onValueChange={(value) => setLayer(value as "ALL" | MemoryLayer)}>
          <SelectTrigger className="w-32" size="sm" aria-label="按层级筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部层级</SelectItem>
            {MEMORY_PROMPT_LAYER_OPTIONS.map((option) => (
              <SelectItem key={option} value={option}>
                {MEMORY_LAYER_META.find((meta) => meta.id === option)?.label ?? option}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={source} onValueChange={setSource}>
          <SelectTrigger className="w-36" size="sm" aria-label="按提示词来源筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部来源</SelectItem>
            {MEMORY_LOG_SOURCE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={!assetName || query.isFetching}
          onClick={() => query.refetch()}
        >
          <RefreshCw className={query.isFetching ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      <div className="rounded-xl border bg-card">
        {!assetName ? (
          <MemoryEmptyState
            title="选择记忆块查看生成日志"
            description="生成日志按记忆资产作用域记录 L1–L3 的生成过程。"
          />
        ) : query.isLoading ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 5 }, (_, index) => (
              <Skeleton key={index} className="h-9 w-full" />
            ))}
          </div>
        ) : query.isError ? (
          <MemoryErrorState
            message={resolveMemoryApiError(query.error)}
            onRetry={() => query.refetch()}
          />
        ) : items.length === 0 ? (
          <MemoryEmptyState
            title="暂无生成日志"
            description="该记忆块在当前筛选条件下没有生成记录。"
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>时间</TableHead>
                <TableHead>层级</TableHead>
                <TableHead>来源</TableHead>
                <TableHead>模型</TableHead>
                <TableHead>记忆 ID</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatUserDateTime(item.createdAt ?? undefined)}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">
                      {MEMORY_LAYER_META.find((meta) => meta.id === item.memoryLevel)?.label ??
                        item.memoryLevel ??
                        "—"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs">
                    {item.promptSource
                      ? (MEMORY_PROMPT_SOURCE_LABELS[item.promptSource.toUpperCase()] ??
                        item.promptSource)
                      : "—"}
                  </TableCell>
                  <TableCell className="font-mono text-xs">{item.modelCode ?? "—"}</TableCell>
                  <TableCell className="font-mono text-xs">{item.memoryId ?? "—"}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      type="button"
                      variant="ghost"
                      size="xs"
                      onClick={() => setLogId(item.id)}
                    >
                      详情
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {assetName && query.hasNextPage ? (
          <div className="flex justify-center border-t p-3">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={query.isFetchingNextPage}
              onClick={() => query.fetchNextPage()}
            >
              {query.isFetchingNextPage ? "加载中…" : "加载更早日志"}
            </Button>
          </div>
        ) : null}
      </div>

      {logId !== null ? (
        <GenerationLogDetailSheet logId={logId} onClose={() => setLogId(null)} />
      ) : null}
    </div>
  );
}
