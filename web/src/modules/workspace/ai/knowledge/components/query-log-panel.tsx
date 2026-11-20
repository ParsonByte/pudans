import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { RefreshCw } from "lucide-react";

import { DataTablePagination, LongText } from "@/components/data-table";
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
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import {
  QUERY_LOG_LIMIT,
  fetchKnowledgeBaseQueryLogs,
  resolveKnowledgeBaseApiError,
} from "../lib/api";
import { formatLatency, formatScore } from "../lib/model";
import { KnowledgeErrorState, KnowledgeTableSkeleton } from "./knowledge-states";

type QueryLogPanelProps = {
  knowledgeBaseName: string;
};

const QUERY_LOG_COLUMNS = 7;

export function QueryLogPanel({ knowledgeBaseName }: QueryLogPanelProps) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const logsQuery = useQuery({
    queryKey: ["knowledge-base-query-logs", knowledgeBaseName],
    queryFn: () => fetchKnowledgeBaseQueryLogs(knowledgeBaseName, QUERY_LOG_LIMIT),
    retry: false,
  });

  const logs = logsQuery.data ?? [];
  const pageCount = Math.max(1, Math.ceil(logs.length / size));
  const currentPage = Math.min(page, pageCount);
  const pageLogs = logs.slice((currentPage - 1) * size, currentPage * size);
  const errorMessage =
    isAxiosError(logsQuery.error) && logsQuery.error.response?.status === 403
      ? "查询日志仅对具备 knowledgebases/querylogs 权限的管理员开放。"
      : resolveKnowledgeBaseApiError(logsQuery.error);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-muted-foreground">
          仅显示最近 {QUERY_LOG_LIMIT} 条检索记录，最新在前。
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => void logsQuery.refetch()}
          disabled={logsQuery.isFetching}
        >
          <RefreshCw className={logsQuery.isFetching ? "animate-spin" : undefined} />
          刷新
        </Button>
      </div>

      {logsQuery.isError ? (
        <KnowledgeErrorState
          title="无法加载查询日志"
          message={errorMessage}
          onRetry={() => void logsQuery.refetch()}
          className="border-dashed bg-transparent"
        />
      ) : (
        <div className="overflow-hidden border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-4">时间</TableHead>
                <TableHead className="px-4">查询</TableHead>
                <TableHead className="px-4">命中</TableHead>
                <TableHead className="px-4">最高分</TableHead>
                <TableHead className="px-4">重排</TableHead>
                <TableHead className="px-4">耗时</TableHead>
                <TableHead className="px-4">用户</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {logsQuery.isPending ? (
                <KnowledgeTableSkeleton columns={QUERY_LOG_COLUMNS} rows={4} />
              ) : pageLogs.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={QUERY_LOG_COLUMNS}>
                    <ConsoleEmpty>暂无检索日志。执行检索测试后会在此记录。</ConsoleEmpty>
                  </TableCell>
                </TableRow>
              ) : (
                pageLogs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell className="px-4 py-3 text-xs text-muted-foreground">
                      {formatUserDateTime(log.createdAt)}
                    </TableCell>
                    <TableCell className="px-4 py-3">
                      <LongText className="max-w-72 text-sm">{log.query}</LongText>
                      <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">
                        {log.requestId || "—"}
                      </p>
                    </TableCell>
                    <TableCell className="px-4 py-3 tabular-nums">{log.hitCount}</TableCell>
                    <TableCell className="px-4 py-3 tabular-nums">
                      {formatScore(log.topScore)}
                    </TableCell>
                    <TableCell className="px-4 py-3">
                      <Badge variant={log.rerankUsed ? "secondary" : "outline"}>
                        {log.rerankUsed ? "已重排" : "未重排"}
                      </Badge>
                    </TableCell>
                    <TableCell className="px-4 py-3 text-xs text-muted-foreground tabular-nums">
                      {formatLatency(log.latencyMs)}
                    </TableCell>
                    <TableCell className="px-4 py-3 font-mono text-xs text-muted-foreground">
                      {log.userId || "—"}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {!logsQuery.isError && logs.length > 0 ? (
        <DataTablePagination
          currentPage={currentPage}
          pageCount={pageCount}
          pageSize={size}
          onPageChange={setPage}
          onPageSizeChange={(value) => {
            setSize(value);
            setPage(1);
          }}
          disabled={logsQuery.isFetching}
        />
      ) : null}
    </div>
  );
}
