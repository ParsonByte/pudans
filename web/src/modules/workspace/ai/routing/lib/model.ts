import { beijingDateKey, serverToday } from "@/lib/date";

import type {
  AiModelItem,
  AiModelTier,
  AiProviderAccountItem,
  AiProviderAffinity,
  AiProviderItem,
  AiRouteGroupItem,
  AiRouteGroupMode,
} from "../../providers/lib/api";
import {
  AFFINITY_OPTIONS,
  MODEL_TIER_OPTIONS,
  formatNumber,
  optionLabel,
} from "../../providers/lib/model";
import type { AiRoutingUser } from "./api";

export type AiRoutingLookups = {
  providers: Map<string, AiProviderItem>;
  accounts: Map<string, AiProviderAccountItem>;
  models: Map<string, AiModelItem>;
  routeGroups: Map<string, AiRouteGroupItem>;
  users: Map<string, AiRoutingUser>;
};

export function buildRoutingLookups(
  providers: AiProviderItem[],
  accounts: AiProviderAccountItem[],
  models: AiModelItem[],
  routeGroups: AiRouteGroupItem[],
  users: AiRoutingUser[] = [],
): AiRoutingLookups {
  return {
    providers: new Map(providers.map((provider) => [provider.metadata.name, provider])),
    accounts: new Map(accounts.map((account) => [account.metadata.name, account])),
    models: new Map(models.map((model) => [model.metadata.name, model])),
    routeGroups: new Map(routeGroups.map((group) => [group.metadata.name, group])),
    users: new Map(users.map((user) => [user.userId, user])),
  };
}

export function userDisplayName(userId: string | undefined, lookups: AiRoutingLookups): string {
  if (!userId) {
    return "未知用户";
  }
  const user = lookups.users.get(userId);
  return user?.displayName || user?.username || shortIdentifier(userId, 18);
}

export function providerDisplayName(
  provider: AiProviderItem | undefined,
  fallback: string,
): string {
  return provider?.spec.displayName || provider?.metadata.name || fallback;
}

export function accountDisplayName(
  account: AiProviderAccountItem | undefined,
  fallback: string,
): string {
  return account?.spec.displayName || account?.metadata.name || fallback;
}

export function routeGroupDisplayName(
  group: AiRouteGroupItem | undefined,
  fallback: string,
): string {
  return group?.spec.displayName || group?.metadata.name || fallback;
}

export const ROUTE_MODE_LABELS: Record<AiRouteGroupMode, string> = {
  SMART: "智能",
  ORDER: "顺序",
  ROTATE: "轮询",
  USAGE: "用量",
  PACE: "节奏",
  WEIGHT: "权重",
  FREE: "免费优先",
  FAST: "速度优先",
  CHEAP: "成本优先",
  DRAIN: "排空",
  EXPIRING: "到期优先",
  MANUAL: "手动",
};

const STATUS_LABELS: Record<string, string> = {
  SUCCEEDED: "成功",
  SUCCESS: "成功",
  FAILED: "失败",
  RUNNING: "进行中",
};

const STATUS_CLASSES: Record<string, string> = {
  SUCCEEDED: "bg-success",
  SUCCESS: "bg-success",
  FAILED: "bg-destructive",
  RUNNING: "bg-warning",
};

const AGENT_LABELS: Record<string, string> = {
  "claude-code": "Claude Code",
  openai: "OpenAI",
  "pudans-chat": "PUDANS",
};

const AGENT_ICONS: Record<string, string> = {
  "claude-code": "simple-icons:anthropic",
  openai: "simple-icons:openai",
  "pudans-chat": "lucide:sparkles",
};

const WEEKDAYS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

export const HISTORY_STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "全部状态" },
  { value: "SUCCEEDED", label: "成功" },
  { value: "FAILED", label: "失败" },
  { value: "RUNNING", label: "进行中" },
];

const ROUTE_ELIMINATION_REASON_LABELS: Record<string, string> = {
  DISABLED: "已停用",
  RESTING: "熔断冷却",
  UNHEALTHY: "健康度不足",
  CAPABILITY_MISMATCH: "能力不匹配",
  CONTEXT_EXCEEDED: "超出上下文",
  SPENT: "额度用尽",
  SUNK: "限流冷却",
};

const ROUTE_OUTCOME_LABELS: Record<string, string> = {
  SELECTED: "已选中",
  EXHAUSTED: "候选耗尽",
  STRICT_PINNED_UNAVAILABLE: "指定模型不可用",
};

const COMPLEXITY_LEVEL_LABELS: Record<string, string> = {
  SIMPLE: "简单",
  MEDIUM: "中等",
  COMPLEX: "复杂",
};

const ACCOUNT_HEALTH_LABELS: Record<string, string> = {
  HEALTHY: "正常",
  RESTING: "熔断冷却",
  SUNK: "限流冷却",
  UNHEALTHY: "异常",
};

export function eliminationReasonLabel(reason: string | undefined): string {
  return reason ? (ROUTE_ELIMINATION_REASON_LABELS[reason] ?? reason) : "可用";
}

export function outcomeLabel(outcome: string | undefined): string {
  return outcome ? (ROUTE_OUTCOME_LABELS[outcome] ?? outcome) : "未知";
}

export function complexityLabel(level: string | undefined): string {
  return level ? (COMPLEXITY_LEVEL_LABELS[level] ?? level) : "未分类";
}

export function traceAffinityLabel(affinity: string | undefined): string {
  return optionLabel(AFFINITY_OPTIONS, affinity as AiProviderAffinity | undefined, "自动");
}

export function accountHealthLabel(health: string | undefined): string | undefined {
  return health ? (ACCOUNT_HEALTH_LABELS[health] ?? health) : undefined;
}

export function routeModeLabel(mode: AiRouteGroupMode | undefined): string {
  return mode ? ROUTE_MODE_LABELS[mode] : "智能";
}

export function tierLabel(tier: AiModelTier | undefined): string | undefined {
  return tier ? optionLabel(MODEL_TIER_OPTIONS, tier) : undefined;
}

export function agentLabel(source: string | undefined): string {
  if (!source) {
    return "未知来源";
  }
  return AGENT_LABELS[source] ?? source;
}

export function agentIcon(source: string | undefined): string {
  if (!source) {
    return "lucide:bot";
  }
  return AGENT_ICONS[source] ?? "lucide:bot";
}

export function requestStatusLabel(status: string | undefined): string {
  if (!status) {
    return "未知";
  }
  return STATUS_LABELS[status] ?? status;
}

export function requestStatusDotClass(status: string | undefined): string {
  return STATUS_CLASSES[status ?? ""] ?? "bg-muted-foreground";
}

export function dayKeyLabel(day: string): string {
  const today = serverToday();
  const yesterday = beijingDateKey(new Date(Date.now() - 86_400_000));
  if (day === today) {
    return "今天";
  }
  if (day === yesterday) {
    return "昨天";
  }
  const date = new Date(`${day}T00:00:00Z`);
  if (Number.isNaN(date.getTime())) {
    return day;
  }
  return `${date.getUTCMonth() + 1}月${date.getUTCDate()}日 ${WEEKDAYS[date.getUTCDay()]}`;
}

export function formatRelativeTime(iso: string | undefined): string {
  if (!iso) {
    return "—";
  }
  const timestamp = new Date(iso).getTime();
  if (Number.isNaN(timestamp)) {
    return "—";
  }
  const seconds = Math.max(0, Math.round((Date.now() - timestamp) / 1000));
  if (seconds < 10) {
    return "刚刚";
  }
  if (seconds < 60) {
    return `${seconds} 秒前`;
  }
  if (seconds < 3600) {
    return `${Math.floor(seconds / 60)} 分钟前`;
  }
  if (seconds < 86_400) {
    return `${Math.floor(seconds / 3600)} 小时前`;
  }
  return `${Math.floor(seconds / 86_400)} 天前`;
}

export function formatDuration(millis: number | undefined): string {
  if (millis == null || millis < 0) {
    return "—";
  }
  if (millis < 1000) {
    return `${formatNumber(millis)} 毫秒`;
  }
  return `${formatNumber(millis / 1000, 1)} 秒`;
}

export function formatTokenCount(tokens: number | undefined): string {
  if (tokens == null) {
    return "—";
  }
  if (tokens >= 1_000_000) {
    return `${formatNumber(tokens / 1_000_000, 1)}M`;
  }
  if (tokens >= 1000) {
    return `${formatNumber(tokens / 1000, 1)}K`;
  }
  return formatNumber(tokens);
}

export function formatUnits(units: number | undefined): string | undefined {
  if (units == null) {
    return undefined;
  }
  return `${formatNumber(units, 2)} 标准 Token`;
}

export function cacheHitRate(inputTokens?: number, cacheTokens?: number): number | undefined {
  if (inputTokens == null || inputTokens <= 0 || cacheTokens == null || cacheTokens <= 0) {
    return undefined;
  }
  return Math.min(100, (cacheTokens / inputTokens) * 100);
}

export function shortIdentifier(value: string | undefined, length = 14): string {
  if (!value) {
    return "—";
  }
  return value.length > length ? `${value.slice(0, length)}…` : value;
}
