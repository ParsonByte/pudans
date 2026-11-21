import { useMemo } from "react";

import { addDays, format, parseISO } from "date-fns";

import { D3CalendarHeatmap } from "@/components/charts/d3-calendar-heatmap";
import { Skeleton } from "@/components/ui/skeleton";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import { UsageErrorState } from "../../components/usage-state";
import type { UsageHeatmapBucket, UsageHeatmapMetric } from "../../lib/api";

type RequestHeatmapProps = {
  from: string;
  to: string;
  metric: UsageHeatmapMetric;
  onMetricChange: (metric: UsageHeatmapMetric) => void;
  buckets: UsageHeatmapBucket[];
  loading: boolean;
  source: "server" | "sample" | null;
  errorMessage?: string;
  onRetry: () => void;
};

const WEEK_COUNT = 53;

function metricValue(bucket: UsageHeatmapBucket, metric: UsageHeatmapMetric): number {
  if (metric === "tokens") {
    return bucket.tokens;
  }
  if (metric === "units") {
    return bucket.units;
  }
  return bucket.requests;
}

function formatHeatmapValue(metric: UsageHeatmapMetric, value: number): string {
  if (metric === "tokens") {
    return `${formatTokenCount(value)} Token`;
  }
  if (metric === "units") {
    return `${formatTokenCount(value)} 标准 Token`;
  }
  return `${formatNumber(value)} 次请求`;
}

export function RequestHeatmap({
  from,
  to,
  metric,
  onMetricChange,
  buckets,
  loading,
  source,
  errorMessage,
  onRetry,
}: RequestHeatmapProps) {
  const days = useMemo(() => {
    const values = new Map<string, number>();
    for (const bucket of buckets) {
      values.set(bucket.date, (values.get(bucket.date) ?? 0) + metricValue(bucket, metric));
    }
    const start = parseISO(from);
    const today = parseISO(to);
    const cells: { date: string; value: number }[] = [];
    for (let index = 0; index < WEEK_COUNT * 7; index += 1) {
      const date = addDays(start, index);
      if (date > today) {
        break;
      }
      const key = format(date, "yyyy-MM-dd");
      cells.push({ date: key, value: values.get(key) ?? 0 });
    }
    return cells;
  }, [buckets, from, metric, to]);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2 px-5">
        <p className="text-xs text-muted-foreground">
          {source === "server"
            ? "近 53 周日历热力图，颜色越深表示该指标越高。"
            : source === "sample"
              ? "热力图端点暂不可用，按最近样本请求推导，仅供参考。"
              : "近 53 周日历热力图。"}
        </p>
        <ToggleGroup
          type="single"
          variant="outline"
          size="sm"
          value={metric}
          onValueChange={(value) => {
            if (value === "requests" || value === "tokens" || value === "units") {
              onMetricChange(value);
            }
          }}
        >
          <ToggleGroupItem value="requests">请求</ToggleGroupItem>
          <ToggleGroupItem value="tokens">Token</ToggleGroupItem>
          <ToggleGroupItem value="units">标准 Token</ToggleGroupItem>
        </ToggleGroup>
      </div>

      {loading ? (
        <div className="px-5 pb-5">
          <Skeleton className="h-[118px] w-full max-w-[660px]" />
        </div>
      ) : errorMessage ? (
        <UsageErrorState title="无法加载请求日历" message={errorMessage} onRetry={onRetry} />
      ) : days.length === 0 ? (
        <ConsoleEmpty>所选时间范围内没有请求记录。</ConsoleEmpty>
      ) : (
        <div className="px-5 pb-5">
          <D3CalendarHeatmap
            from={from}
            days={days}
            valueFormatter={(value) => formatHeatmapValue(metric, value)}
            colorTone="chart1"
            ariaLabel="请求日历热力图"
          />
        </div>
      )}
    </div>
  );
}
