import { beijingDateKey, beijingHourOf } from "@/lib/date";

import type {
  UsageSession,
  UsageSessionStats,
  UsageSessionStatsDay,
  UsageSessionStatsHour,
  UsageSessionStatsModel,
  UsageSessionStatsTopSession,
} from "../../lib/api";
import { usageMedian, usagePercentile } from "../../lib/model";

const TOP_SESSION_LIMIT = 8;

export function deriveUsageSessionStats(items: readonly UsageSession[]): UsageSessionStats {
  const messageCounts = items.map((item) => item.messageCount);
  const dayMap = new Map<string, UsageSessionStatsDay>();
  const hourCounts = Array.from({ length: 24 }, () => 0);
  const modelMap = new Map<string, UsageSessionStatsModel>();
  let tokens = 0;
  let units = 0;
  let cacheReadTokens = 0;
  let activeSeconds = 0;

  for (const item of items) {
    tokens += item.totalTokens;
    units += item.units;
    cacheReadTokens += item.cacheTokens;
    activeSeconds += item.durationSeconds;

    const day = beijingDateKey(item.lastActivityAt) ?? beijingDateKey(item.createdAt);
    if (day) {
      const dayEntry =
        dayMap.get(day) ??
        ({
          day,
          sessions: 0,
          messages: 0,
          tokens: 0,
          units: 0,
          activeSeconds: 0,
        } satisfies UsageSessionStatsDay);
      dayEntry.sessions += 1;
      dayEntry.messages += item.messageCount;
      dayEntry.tokens += item.totalTokens;
      dayEntry.units += item.units;
      dayEntry.activeSeconds += item.durationSeconds;
      dayMap.set(day, dayEntry);
    }

    const hour = beijingHourOf(item.lastActivityAt) ?? beijingHourOf(item.createdAt);
    if (hour != null) {
      hourCounts[hour] += 1;
    }

    const modelName = item.model || "未知模型";
    const modelEntry =
      modelMap.get(modelName) ??
      ({
        name: modelName,
        sessions: 0,
        tokens: 0,
        units: 0,
      } satisfies UsageSessionStatsModel);
    modelEntry.sessions += 1;
    modelEntry.tokens += item.totalTokens;
    modelEntry.units += item.units;
    modelMap.set(modelName, modelEntry);
  }

  const hours: UsageSessionStatsHour[] = hourCounts.map((sessions, hour) => ({
    hour,
    sessions,
  }));

  const topSessions: UsageSessionStatsTopSession[] = [...items]
    .sort((left, right) => right.totalTokens - left.totalTokens)
    .slice(0, TOP_SESSION_LIMIT)
    .map((item) => ({
      conversationId: item.conversationId,
      title: item.title,
      agent: item.agent,
      model: item.model,
      messages: item.messageCount,
      tokens: item.totalTokens,
      units: item.units,
      lastActivityAt: item.lastActivityAt,
    }));

  return {
    totals: {
      sessions: items.length,
      medianMessages: usageMedian(messageCounts) ?? 0,
      p90Messages: usagePercentile(messageCounts, 0.9) ?? 0,
      tokens,
      units,
      cacheReadTokens,
      activeSeconds,
    },
    series: [...dayMap.values()].sort((left, right) => left.day.localeCompare(right.day)),
    hours,
    models: [...modelMap.values()].sort((left, right) => right.tokens - left.tokens),
    topSessions,
    shape: [],
  };
}
