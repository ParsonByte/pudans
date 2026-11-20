import { useMemo } from "react";

import { addDays, format, parseISO } from "date-fns";

import { Skeleton } from "@/components/ui/skeleton";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
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

const WEEKDAYS = ["一", "二", "三", "四", "五", "六", "日"];

const WEEK_COUNT = 53;

const CELL_SIZE = 11;

const CELL_GAP = 2;

const OPACITY_LEVELS = [0.18, 0.38, 0.62, 1];

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

  const maxValue = useMemo(() => days.reduce((max, day) => Math.max(max, day.value), 0), [days]);

  const monthLabels = useMemo(() => {
    const labels: { index: number; label: string }[] = [];
    let previousMonth = "";
    for (let week = 0; week < WEEK_COUNT; week += 1) {
      const weekStart = addDays(parseISO(from), week * 7);
      const month = format(weekStart, "yyyy-MM");
      if (month !== previousMonth) {
        labels.push({ index: week, label: format(weekStart, "MM 月") });
        previousMonth = month;
      }
    }
    return labels;
  }, [from]);

  const gridWidth = WEEK_COUNT * (CELL_SIZE + CELL_GAP);

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
        <div className="space-y-3 px-5 pb-5">
          <div className="overflow-x-auto">
            <div className="min-w-max">
              <div className="relative mb-1 h-4" style={{ width: gridWidth, marginLeft: 18 }}>
                {monthLabels.map((month) => (
                  <span
                    key={`${month.index}-${month.label}`}
                    className="absolute top-0 text-[10px] text-muted-foreground"
                    style={{ left: month.index * (CELL_SIZE + CELL_GAP) }}
                  >
                    {month.label}
                  </span>
                ))}
              </div>
              <div className="flex gap-1">
                <div
                  className="grid text-[10px] text-muted-foreground"
                  style={{
                    gridTemplateRows: `repeat(7, ${CELL_SIZE}px)`,
                    rowGap: CELL_GAP,
                  }}
                >
                  {WEEKDAYS.map((weekday) => (
                    <span key={weekday} className="leading-none">
                      {weekday}
                    </span>
                  ))}
                </div>
                <div
                  className="grid"
                  style={{
                    gridAutoFlow: "column",
                    gridTemplateRows: `repeat(7, ${CELL_SIZE}px)`,
                    gridAutoColumns: `${CELL_SIZE}px`,
                    gap: CELL_GAP,
                  }}
                >
                  {days.map((day) => {
                    const ratio = maxValue > 0 ? day.value / maxValue : 0;
                    const level =
                      day.value <= 0
                        ? -1
                        : ratio <= 0.25
                          ? 0
                          : ratio <= 0.5
                            ? 1
                            : ratio <= 0.75
                              ? 2
                              : 3;
                    return (
                      <Tooltip key={day.date}>
                        <TooltipTrigger asChild>
                          <div
                            className="rounded-[2px] border border-transparent bg-muted transition-opacity hover:opacity-80"
                            style={
                              level < 0
                                ? undefined
                                : {
                                    backgroundColor: "var(--chart-1)",
                                    opacity: OPACITY_LEVELS[level],
                                  }
                            }
                          />
                        </TooltipTrigger>
                        <TooltipContent>
                          <p className="font-medium">{day.date}</p>
                          <p>{formatHeatmapValue(metric, day.value)}</p>
                        </TooltipContent>
                      </Tooltip>
                    );
                  })}
                </div>
              </div>
              <div className="mt-2 flex items-center gap-1 text-[10px] text-muted-foreground">
                <span>少</span>
                {OPACITY_LEVELS.map((opacity) => (
                  <span
                    key={opacity}
                    className="size-[10px] rounded-[2px]"
                    style={{ backgroundColor: "var(--chart-1)", opacity }}
                  />
                ))}
                <span>多</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
