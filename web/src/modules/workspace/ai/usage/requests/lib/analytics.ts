import { format, parseISO, startOfWeek, subDays, subWeeks } from "date-fns";

import { beijingDateKey, serverToday } from "@/lib/date";

import type { UsageRequestItem, UsageSpeedGroupBy, UsageSpeedItem } from "../../lib/api";
import { usageMedian } from "../../lib/model";

export type UsageTrendMetric = "tokens" | "units" | "calls" | "speed";

export type UsageTrendSplit = "model" | "provider" | "agent";

type UsageTrendSeries = {
  key: string;
  label: string;
  color: string;
};

type UsageTrendPoint = {
  day: string;
  [key: string]: string | number;
};

export type UsageTrend = {
  points: UsageTrendPoint[];
  series: UsageTrendSeries[];
};

const SPLIT_SERIES_LIMIT = 5;

const SPLIT_OTHER_LABEL = "其他";

const SERIES_COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
  "var(--muted-foreground)",
];

const HEATMAP_WEEKS = 53;

function splitLabel(item: UsageRequestItem, split: UsageTrendSplit): string {
  if (split === "provider") {
    return item.provider || "未知供应商";
  }
  if (split === "agent") {
    return item.agent || "未知 Agent";
  }
  return item.model || "未知模型";
}

export function buildUsageTrend(
  items: readonly UsageRequestItem[],
  metric: UsageTrendMetric,
  split: UsageTrendSplit,
): UsageTrend {
  const totals = new Map<string, number>();
  const byDay = new Map<string, Map<string, number>>();
  const dayCounts = new Map<string, Map<string, number>>();

  for (const item of items) {
    const day = beijingDateKey(item.createdAt);
    if (!day) {
      continue;
    }
    const label = splitLabel(item, split);
    const dayValues = byDay.get(day) ?? new Map<string, number>();
    byDay.set(day, dayValues);
    if (metric === "speed") {
      if (item.tps == null || item.tps <= 0) {
        continue;
      }
      const counts = dayCounts.get(day) ?? new Map<string, number>();
      counts.set(label, (counts.get(label) ?? 0) + 1);
      dayCounts.set(day, counts);
      dayValues.set(label, (dayValues.get(label) ?? 0) + item.tps);
      totals.set(label, (totals.get(label) ?? 0) + 1);
      continue;
    }
    const value =
      metric === "tokens" ? (item.totalTokens ?? 0) : metric === "units" ? (item.units ?? 0) : 1;
    dayValues.set(label, (dayValues.get(label) ?? 0) + value);
    totals.set(label, (totals.get(label) ?? 0) + value);
  }

  const ranked = [...totals.entries()].sort((left, right) => right[1] - left[1]);
  const kept = ranked.slice(0, SPLIT_SERIES_LIMIT).map(([label]) => label);
  const hasOther = ranked.length > kept.length;
  const series = kept.map((label, index) => ({
    key: `value${index}`,
    label,
    color: SERIES_COLORS[index] ?? SERIES_COLORS[SERIES_COLORS.length - 1],
  }));
  if (hasOther) {
    series.push({
      key: "other",
      label: SPLIT_OTHER_LABEL,
      color: SERIES_COLORS[SERIES_COLORS.length - 1],
    });
  }

  const points = [...byDay.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([day, values]) => {
      const point: UsageTrendPoint = { day };
      series.forEach((entry) => {
        point[entry.key] = 0;
      });
      let otherSum = 0;
      let otherCount = 0;
      for (const [label, value] of values) {
        const index = kept.indexOf(label);
        if (metric === "speed") {
          const count = dayCounts.get(day)?.get(label) ?? 0;
          const average = count > 0 ? value / count : 0;
          if (index >= 0) {
            point[series[index].key] = average;
          } else if (hasOther) {
            otherSum += value;
            otherCount += count;
          }
        } else if (index >= 0) {
          point[series[index].key] = value;
        } else if (hasOther) {
          point.other = Number(point.other ?? 0) + value;
        }
      }
      if (metric === "speed" && hasOther) {
        point.other = otherCount > 0 ? otherSum / otherCount : 0;
      }
      return point;
    });

  return { points, series };
}

export function deriveUsageSpeed(
  items: readonly UsageRequestItem[],
  groupBy: UsageSpeedGroupBy,
): UsageSpeedItem[] {
  const groups = new Map<
    string,
    { provider: string | null; requests: number; failures: number; ttft: number[]; tps: number[] }
  >();
  for (const item of items) {
    const name = groupBy === "provider" ? item.provider || "未知供应商" : item.model || "未知模型";
    const group = groups.get(name) ?? {
      provider: groupBy === "provider" ? name : item.provider,
      requests: 0,
      failures: 0,
      ttft: [],
      tps: [],
    };
    group.requests += 1;
    if (item.status === "FAILED") {
      group.failures += 1;
    }
    if (item.ttftMs != null && item.ttftMs >= 0) {
      group.ttft.push(item.ttftMs);
    }
    if (item.tps != null && item.tps > 0) {
      group.tps.push(item.tps);
    }
    groups.set(name, group);
  }
  return [...groups.entries()]
    .map(([name, group]) => ({
      name,
      provider: group.provider,
      requests: group.requests,
      failures: group.failures,
      avgTtftMs: average(group.ttft),
      p50TtftMs: usageMedian(group.ttft),
      avgTps: average(group.tps),
      p50Tps: usageMedian(group.tps),
    }))
    .sort((left, right) => {
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
    })
    .slice(0, 10);
}

function average(values: readonly number[]): number | null {
  if (values.length === 0) {
    return null;
  }
  return values.reduce((total, value) => total + value, 0) / values.length;
}

export function usageHeatmapWindow(): { from: string; to: string } {
  const today = parseISO(serverToday());
  const start = subWeeks(startOfWeek(today, { weekStartsOn: 1 }), HEATMAP_WEEKS - 1);
  const earliest = subDays(today, 365);
  return {
    from: format(start < earliest ? earliest : start, "yyyy-MM-dd"),
    to: format(today, "yyyy-MM-dd"),
  };
}

export function requestPageMedians(items: readonly UsageRequestItem[]): {
  ttftMs: number | null;
  tps: number | null;
} {
  const ttft = items
    .map((item) => item.ttftMs)
    .filter((value): value is number => value != null && value >= 0);
  const tps = items
    .map((item) => item.tps)
    .filter((value): value is number => value != null && value > 0);
  return { ttftMs: usageMedian(ttft), tps: usageMedian(tps) };
}
