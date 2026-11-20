import type { UsageContextPartKey, UsageContextTotals } from "../../lib/api";

export type ContextPartDefinition = {
  key: UsageContextPartKey;
  label: string;
  description: string;
  color: string;
  totalsKey: keyof UsageContextTotals;
};

export const CONTEXT_PARTS: ContextPartDefinition[] = [
  {
    key: "system",
    label: "系统提示",
    description: "Agent 系统提示词与工具定义",
    color: "var(--chart-1)",
    totalsKey: "systemTokens",
  },
  {
    key: "history",
    label: "历史消息",
    description: "输入 Token 扣除系统、记忆、知识库与用户输入后的剩余",
    color: "var(--chart-2)",
    totalsKey: "historyTokens",
  },
  {
    key: "memory",
    label: "记忆",
    description: "记忆提示注入的上下文",
    color: "var(--chart-3)",
    totalsKey: "memoryTokens",
  },
  {
    key: "kb",
    label: "知识库",
    description: "检索命中的知识库片段",
    color: "var(--chart-4)",
    totalsKey: "kbTokens",
  },
  {
    key: "user",
    label: "用户输入",
    description: "本次请求的用户消息",
    color: "var(--chart-5)",
    totalsKey: "userTokens",
  },
  {
    key: "output",
    label: "输出",
    description: "模型生成的响应 Token，单独统计",
    color: "var(--primary)",
    totalsKey: "outputTokens",
  },
];

export const CONTEXT_WINDOW_PARTS = CONTEXT_PARTS.filter((part) => part.key !== "output");

export type ContextHealth = "healthy" | "warning" | "critical" | "unknown";

const WARNING_THRESHOLD = 0.75;

const CRITICAL_THRESHOLD = 0.9;

export function contextHealth(utilization: number | null): ContextHealth {
  if (utilization == null || !Number.isFinite(utilization)) {
    return "unknown";
  }
  if (utilization >= CRITICAL_THRESHOLD) {
    return "critical";
  }
  if (utilization >= WARNING_THRESHOLD) {
    return "warning";
  }
  return "healthy";
}

export function contextHealthLabel(health: ContextHealth): string {
  if (health === "critical") {
    return "高危";
  }
  if (health === "warning") {
    return "关注";
  }
  if (health === "healthy") {
    return "健康";
  }
  return "未知";
}

export function contextHealthColorClass(health: ContextHealth): string {
  if (health === "critical") {
    return "bg-destructive";
  }
  if (health === "warning") {
    return "bg-warning";
  }
  if (health === "healthy") {
    return "bg-success";
  }
  return "bg-muted";
}

export function contextHealthBadgeVariant(
  health: ContextHealth,
): "secondary" | "outline" | "destructive" {
  if (health === "critical") {
    return "destructive";
  }
  if (health === "warning") {
    return "outline";
  }
  return "secondary";
}

export function contextWindowTokens(parts: Record<UsageContextPartKey, number>): number {
  return CONTEXT_WINDOW_PARTS.reduce((sum, part) => sum + Math.max(parts[part.key], 0), 0);
}
