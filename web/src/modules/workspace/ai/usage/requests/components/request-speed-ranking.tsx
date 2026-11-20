import { useMemo } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatDuration } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import { UsageErrorState } from "../../components/usage-state";
import type { UsageSpeedGroupBy, UsageSpeedItem } from "../../lib/api";
import { formatPercent } from "../../lib/model";

type RequestSpeedRankingProps = {
  items: UsageSpeedItem[];
  groupBy: UsageSpeedGroupBy;
  onGroupByChange: (groupBy: UsageSpeedGroupBy) => void;
  loading: boolean;
  source: "server" | "sample" | null;
  errorMessage?: string;
  onRetry: () => void;
};

export function RequestSpeedRanking({
  items,
  groupBy,
  onGroupByChange,
  loading,
  source,
  errorMessage,
  onRetry,
}: RequestSpeedRankingProps) {
  const ranked = useMemo(
    () =>
      [...items].sort((left, right) => {
        if (left.p50Tps == null && right.p50Tps == null) {
          return right.requests - left.requests;
        }
        if (left.p50Tps == null) {
          return 1;
        }
        if (right.p50Tps == null) {
          return -1;
        }
        return right.p50Tps - left.p50Tps;
      }),
    [items],
  );
  const maxTps = ranked.reduce((max, item) => Math.max(max, item.p50Tps ?? 0), 0);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2 px-5">
        <p className="text-xs text-muted-foreground">
          {source === "server"
            ? "按中位吞吐排序，数据来自服务端速度统计。"
            : source === "sample"
              ? "速度端点暂不可用，按最近样本请求实时计算，仅供参考。"
              : "按中位吞吐排序。"}
        </p>
        <ToggleGroup
          type="single"
          variant="outline"
          size="sm"
          value={groupBy}
          onValueChange={(value) => {
            if (value === "model" || value === "provider") {
              onGroupByChange(value);
            }
          }}
        >
          <ToggleGroupItem value="model">按模型</ToggleGroupItem>
          <ToggleGroupItem value="provider">按供应商</ToggleGroupItem>
        </ToggleGroup>
      </div>

      {loading ? (
        <div className="space-y-2 px-5 pb-5">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-10 w-full" />
          ))}
        </div>
      ) : errorMessage ? (
        <UsageErrorState title="无法加载速度排行" message={errorMessage} onRetry={onRetry} />
      ) : ranked.length === 0 ? (
        <ConsoleEmpty>所选时间范围内没有可用的响应速度数据。</ConsoleEmpty>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="px-5">{groupBy === "provider" ? "供应商" : "模型"}</TableHead>
              <TableHead className="px-5 text-end">请求数</TableHead>
              <TableHead className="px-5 text-end">失败率</TableHead>
              <TableHead className="px-5 text-end">首 Token 中位</TableHead>
              <TableHead className="px-5 text-end">吞吐中位</TableHead>
              <TableHead className="w-40 px-5">相对速度</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {ranked.map((item) => {
              const failureRate = item.requests > 0 ? item.failures / item.requests : null;
              return (
                <TableRow key={item.name}>
                  <TableCell className="px-5 py-3">
                    <p className="text-sm">{item.name}</p>
                    {groupBy === "model" && item.provider ? (
                      <p className="text-[10px] text-muted-foreground">{item.provider}</p>
                    ) : null}
                  </TableCell>
                  <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                    {formatNumber(item.requests)}
                  </TableCell>
                  <TableCell className="px-5 py-3 text-end text-xs tabular-nums">
                    {formatPercent(failureRate)}
                  </TableCell>
                  <TableCell className="px-5 py-3 text-end text-xs tabular-nums">
                    {item.p50TtftMs == null ? "—" : formatDuration(item.p50TtftMs)}
                  </TableCell>
                  <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                    {item.p50Tps == null ? "—" : `${formatNumber(item.p50Tps, 1)} token/秒`}
                  </TableCell>
                  <TableCell className="px-5 py-3">
                    <div className="h-2 w-full overflow-hidden bg-muted">
                      <div
                        className="h-full bg-chart-1"
                        style={{
                          width: `${maxTps > 0 ? Math.max(((item.p50Tps ?? 0) / maxTps) * 100, 2) : 0}%`,
                        }}
                      />
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
