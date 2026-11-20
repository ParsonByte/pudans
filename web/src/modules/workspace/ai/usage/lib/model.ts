import { differenceInCalendarDays, format, parseISO, subDays } from "date-fns";

import { formatTrendDay } from "@/lib/date";

import { formatNumber } from "../../providers/lib/model";
import {
  cacheHitRate,
  formatDuration,
  formatPoints,
  formatTokenCount,
  requestStatusLabel,
} from "../../routing/lib/model";
import type { UsageBucket, UsageScope, UsageTotals } from "./api";

export const SERVER_TIME_ZONE = "Asia/Shanghai";

const MAX_USAGE_RANGE_DAYS = 366;

export type UsageRangePreset = "today" | "24h" | "7d" | "30d" | "90d" | "custom";

export type UsageRangeOptionValue = UsageRangePreset | "all";

export type UsageDateRangeValue = {
  preset: UsageRangePreset;
  from: string;
  to: string;
};

export const USAGE_RANGE_OPTIONS: {
  value: UsageRangeOptionValue;
  label: string;
  disabled?: boolean;
}[] = [
  { value: "today", label: "今日" },
  { value: "24h", label: "近 24 小时" },
  { value: "7d", label: "近 7 天" },
  { value: "30d", label: "近 30 天" },
  { value: "90d", label: "近 90 天" },
  { value: "all", label: "全部", disabled: true },
  { value: "custom", label: "自定义" },
];

export const USAGE_ALL_RANGE_HINT =
  "“全部”时间范围尚未得到用量端点支持，暂不可选；最长可选 366 天。";

export const USAGE_SCOPE_OPTIONS: { value: UsageScope; label: string }[] = [
  { value: "self", label: "本人" },
  { value: "all", label: "全站" },
];

export const USAGE_STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "SUCCEEDED", label: requestStatusLabel("SUCCEEDED") },
  { value: "FAILED", label: requestStatusLabel("FAILED") },
  { value: "RUNNING", label: requestStatusLabel("RUNNING") },
];

export const USAGE_SESSION_STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "ACTIVE", label: sessionStatusLabel("ACTIVE") },
  { value: "ARCHIVED", label: sessionStatusLabel("ARCHIVED") },
  { value: "DELETED", label: sessionStatusLabel("DELETED") },
];

export function serverToday(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: SERVER_TIME_ZONE }).format(new Date());
}

export function usageDateRange(preset: Exclude<UsageRangePreset, "custom">): UsageDateRangeValue {
  const today = serverToday();
  const offsets: Record<Exclude<UsageRangePreset, "custom">, number> = {
    today: 0,
    "24h": 1,
    "7d": 6,
    "30d": 29,
    "90d": 89,
  };
  const days = offsets[preset];
  const from = days === 0 ? today : format(subDays(parseISO(today), days), "yyyy-MM-dd");
  return { preset, from, to: today };
}

export function validateUsageDateRange(from: string, to: string): string | null {
  if (!from || !to) {
    return "请选择开始和结束日期。";
  }
  if (from > to) {
    return "开始日期不能晚于结束日期。";
  }
  const span = differenceInCalendarDays(parseISO(to), parseISO(from)) + 1;
  if (span > MAX_USAGE_RANGE_DAYS) {
    return `日期范围不能超过 ${MAX_USAGE_RANGE_DAYS} 天。`;
  }
  return null;
}

export function formatPercent(value: number | null | undefined, digits = 1): string {
  if (value == null || !Number.isFinite(value)) {
    return "—";
  }
  return `${formatNumber(value * 100, digits)}%`;
}

export function formatSessionDuration(seconds: number | null | undefined): string {
  if (seconds == null || seconds <= 0) {
    return "0 秒";
  }
  const parts: string[] = [];
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) {
    parts.push(`${days} 天`);
  }
  if (hours > 0) {
    parts.push(`${hours} 小时`);
  }
  if (minutes > 0) {
    parts.push(`${minutes} 分`);
  }
  if (parts.length === 0) {
    parts.push(`${seconds} 秒`);
  }
  return parts.join(" ");
}

export function usageExportFilename(prefix: string): string {
  return `${prefix}-${format(new Date(), "yyyyMMdd-HHmm")}.csv`;
}

export type UsageKpiItem = {
  label: string;
  value: string;
  hint?: string;
};

export function resolveUsageCacheHitRate(
  totals: Pick<UsageTotals, "cacheHitRate" | "inputTokens" | "cacheTokens">,
): number | null {
  if (totals.cacheHitRate != null) {
    return totals.cacheHitRate;
  }
  const rate = cacheHitRate(totals.inputTokens + totals.cacheTokens, totals.cacheTokens);
  return rate == null ? null : rate / 100;
}

export function usageMedian(values: readonly number[]): number | null {
  if (values.length === 0) {
    return null;
  }
  const sorted = [...values].sort((left, right) => left - right);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
}

export function usagePercentile(values: readonly number[], ratio: number): number | null {
  const finite = values.filter((value) => Number.isFinite(value));
  if (finite.length === 0) {
    return null;
  }
  const sorted = [...finite].sort((left, right) => left - right);
  const position = Math.min(Math.max(ratio, 0), 1) * (sorted.length - 1);
  const lower = Math.floor(position);
  const upper = Math.ceil(position);
  if (lower === upper) {
    return sorted[lower];
  }
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
}

export function serverDayKey(value: string): string | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Intl.DateTimeFormat("en-CA", { timeZone: SERVER_TIME_ZONE }).format(date);
}

export function serverHourOf(value: string): number | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  const hour = new Intl.DateTimeFormat("en-GB", {
    timeZone: SERVER_TIME_ZONE,
    hour: "numeric",
    hour12: false,
  })
    .formatToParts(date)
    .find((part) => part.type === "hour")?.value;
  if (hour == null) {
    return null;
  }
  const parsed = Number(hour);
  return Number.isFinite(parsed) ? parsed % 24 : null;
}

export function formatUsageBucketLabel(value: string, bucket: UsageBucket): string {
  if (bucket === "hour") {
    const hour = value.includes("T") ? value.slice(11, 16) : value;
    return hour.includes(":") ? hour : `${hour}:00`;
  }
  return formatTrendDay(value);
}

export function buildUsageTotalsKpis(
  totals: UsageTotals | undefined,
  includePerformance = true,
): UsageKpiItem[] {
  if (!totals) {
    return [];
  }
  const successRate = totals.requests > 0 ? totals.succeeded / totals.requests : null;
  const retried = totals.retriedRequests ?? 0;
  const canceled = totals.canceledRequests ?? 0;
  const unpriced = totals.unpricedRequests ?? 0;
  const reasoning = totals.reasoningTokens ?? 0;
  const items: UsageKpiItem[] = [
    {
      label: "请求总数",
      value: formatNumber(totals.requests),
      hint: [
        `成功 ${formatNumber(totals.succeeded)}`,
        `失败 ${formatNumber(totals.failed)}`,
        `进行中 ${formatNumber(totals.running)}`,
        retried > 0 ? `重试 ${formatNumber(retried)}` : "",
        canceled > 0 ? `取消 ${formatNumber(canceled)}` : "",
      ]
        .filter(Boolean)
        .join(" · "),
    },
    {
      label: "成功率",
      value: formatPercent(successRate),
      hint: `成功 ${formatNumber(totals.succeeded)} 次`,
    },
    {
      label: "缓存命中率",
      value: formatPercent(resolveUsageCacheHitRate(totals)),
      hint: `缓存 Token ${formatTokenCount(totals.cacheTokens)} · 输入 ${formatTokenCount(totals.inputTokens)}`,
    },
    {
      label: "Token 总量",
      value: formatTokenCount(totals.totalTokens),
      hint: [
        `输入 ${formatTokenCount(totals.inputTokens)}`,
        `缓存 ${formatTokenCount(totals.cacheTokens)}`,
        `输出 ${formatTokenCount(totals.outputTokens)}`,
        reasoning > 0 ? `推理 ${formatTokenCount(reasoning)}` : "",
      ]
        .filter(Boolean)
        .join(" · "),
    },
    {
      label: "积分消耗",
      value: formatPoints(totals.points) ?? "0 点",
      hint:
        unpriced > 0 ? `已结算请求累计 · 未计价 ${formatNumber(unpriced)} 次` : "按已结算请求累计",
    },
  ];
  if (!includePerformance) {
    return items;
  }
  items.push(
    {
      label: "平均延迟",
      value: totals.latencyMs == null ? "—" : formatDuration(totals.latencyMs),
      hint: "仅统计有延迟记录的请求",
    },
    {
      label: "平均首 Token",
      value: totals.ttftMs == null ? "—" : formatDuration(totals.ttftMs),
      hint: "首个响应 Token 耗时",
    },
    {
      label: "平均吞吐",
      value: totals.tps == null ? "—" : `${formatNumber(totals.tps, 1)} token/秒`,
      hint: "输出 Token 速率",
    },
  );
  return items;
}

export function messageRoleLabel(role: string): string {
  const labels: Record<string, string> = {
    user: "用户",
    assistant: "助手",
    system: "系统",
    tool: "工具",
  };
  return labels[role] ?? role;
}

export function sessionStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    ACTIVE: "活跃",
    ARCHIVED: "已归档",
    DELETED: "已删除",
  };
  return labels[status] ?? status;
}

export function billingStatusLabel(status: string | null | undefined): string {
  const labels: Record<string, string> = {
    SETTLED: "已结算",
    RELEASED: "已释放",
    NONE: "未计费",
  };
  return status ? (labels[status] ?? status) : "—";
}

export function usageSourceLabel(source: string | null | undefined): string {
  const labels: Record<string, string> = {
    PROVIDER: "供应商上报",
    ESTIMATED: "系统估算",
  };
  return source ? (labels[source] ?? source) : "—";
}

export function walletTxnTypeLabel(txnType: string): string {
  const labels: Record<string, string> = {
    GRANT: "发放",
    DEDUCT: "扣减",
    SETTLE: "结算",
    RELEASE: "释放",
    FREEZE: "冻结",
    UNFREEZE: "解冻",
  };
  return labels[txnType] ?? txnType;
}
