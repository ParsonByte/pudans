"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";

import { DataTablePagination } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDuration, requestStatusLabel } from "@/modules/workspace/ai/routing/lib/model";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { GenerationDetailDialog } from "./components/generation-detail-dialog";
import {
  AI_GENERATIONS_QUERY_KEY,
  GENERATION_LIST_LIMIT,
  fetchAiGenerations,
  resolveAiGenerationApiError,
} from "./lib/api";
import {
  generationCapabilityLabel,
  generationStatusVariant,
  generationUnitsLabel,
} from "./lib/model";

const PAGE_TITLE = "生成历史";

const TABLE_COLUMNS = 7;

export function AiGenerationsView() {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [detailRequestId, setDetailRequestId] = useState<string | null>(null);

  const generationsQuery = useQuery({
    queryKey: AI_GENERATIONS_QUERY_KEY,
    queryFn: () => fetchAiGenerations(GENERATION_LIST_LIMIT),
    retry: false,
  });

  const items = generationsQuery.data ?? [];
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const pageItems = items.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  return (
    <ConsolePage title={PAGE_TITLE}>
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-2xl font-bold tracking-tight">{PAGE_TITLE}</h2>
            <p className="text-xs text-muted-foreground">
              查看最近 {GENERATION_LIST_LIMIT} 条生成记录，最新在前。
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={() => void generationsQuery.refetch()}
            disabled={generationsQuery.isFetching}
          >
            {generationsQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>

        <ConsoleSection className="overflow-hidden">
          <ConsoleSectionHeader
            title="生成记录"
            description="点击任意记录查看输出、用量与计费信息。"
            action={
              generationsQuery.data ? (
                <Badge variant="secondary">最近 {items.length} 条</Badge>
              ) : null
            }
          />
          {generationsQuery.isError ? (
            <ConsoleState
              variant="error"
              title="无法加载生成历史"
              description={resolveAiGenerationApiError(generationsQuery.error)}
              onRetry={() => void generationsQuery.refetch()}
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-5">状态</TableHead>
                  <TableHead className="px-5">能力 / 模型</TableHead>
                  <TableHead className="px-5">供应商 / 账户</TableHead>
                  <TableHead className="px-5 text-end">标准 Token</TableHead>
                  <TableHead className="px-5 text-end">延迟</TableHead>
                  <TableHead className="px-5">创建时间</TableHead>
                  <TableHead className="px-5 text-end">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {generationsQuery.isPending ? (
                  <ConsoleTableSkeleton
                    columns={TABLE_COLUMNS}
                    rows={6}
                    cellClassName="px-5 py-3"
                  />
                ) : pageItems.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={TABLE_COLUMNS}>
                      <ConsoleEmpty>
                        还没有生成记录。通过对话生成图片、视频或音频后，会在这里显示。
                      </ConsoleEmpty>
                    </TableCell>
                  </TableRow>
                ) : (
                  pageItems.map((generation) => (
                    <TableRow
                      key={generation.requestId}
                      className="cursor-pointer"
                      onClick={() => setDetailRequestId(generation.requestId)}
                    >
                      <TableCell className="px-5 py-3">
                        <div className="flex flex-col items-start gap-1">
                          <Badge variant={generationStatusVariant(generation.status)}>
                            {requestStatusLabel(generation.status)}
                          </Badge>
                          {generation.errorCode ? (
                            <span className="font-mono text-[10px] text-destructive">
                              {generation.errorCode}
                            </span>
                          ) : null}
                        </div>
                      </TableCell>
                      <TableCell className="px-5 py-3">
                        <p className="text-sm">
                          {generationCapabilityLabel(generation.capability)}
                        </p>
                        <p className="text-[10px] text-muted-foreground">
                          {generation.model || "—"}
                        </p>
                      </TableCell>
                      <TableCell className="px-5 py-3">
                        <p className="text-sm">{generation.provider || "—"}</p>
                        <p className="text-[10px] text-muted-foreground">
                          {generation.account || "—"}
                        </p>
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                        {generationUnitsLabel(generation.units)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end text-xs text-muted-foreground tabular-nums">
                        {formatDuration(generation.latencyMs ?? undefined)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
                        {formatUserDateTime(generation.createdAt)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={(event) => {
                            event.stopPropagation();
                            setDetailRequestId(generation.requestId);
                          }}
                        >
                          查看
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          )}
        </ConsoleSection>

        {!generationsQuery.isError && items.length > 0 ? (
          <DataTablePagination
            currentPage={currentPage}
            pageCount={pageCount}
            pageSize={pageSize}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
              setPageSize(size);
              setPage(1);
            }}
            disabled={generationsQuery.isFetching}
            className="mt-auto"
          />
        ) : null}
      </div>

      {detailRequestId ? (
        <GenerationDetailDialog
          requestId={detailRequestId}
          onClose={() => setDetailRequestId(null)}
        />
      ) : null}
    </ConsolePage>
  );
}
