"use client";

import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";

import type { KnowledgeBaseStats, MemoryStats } from "../lib/api";

type UsageAssetStatsProps = {
  memory?: MemoryStats;
  knowledgeBase?: KnowledgeBaseStats;
};

export function UsageAssetStats({ memory, knowledgeBase }: UsageAssetStatsProps) {
  return (
    <div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-3">
      <AssetMetric label="L1 原子记忆" value={memory?.l1Total} />
      <AssetMetric label="L1 活跃记忆" value={memory?.l1Active} />
      <AssetMetric label="L2 场景记忆" value={memory?.l2Total} />
      <AssetMetric label="L3 核心画像" value={memory?.l3Total} />
      <AssetMetric label="知识库文档" value={knowledgeBase?.documents} />
      <AssetMetric label="就绪文档" value={knowledgeBase?.ready} />
      <AssetMetric label="失败文档" value={knowledgeBase?.failed} tone="destructive" />
      <AssetMetric label="文档分段" value={knowledgeBase?.chunks} />
      <AssetMetric label="知识库查询" value={knowledgeBase?.queries} />
    </div>
  );
}

type AssetMetricProps = {
  label: string;
  value: number | undefined;
  tone?: "destructive";
};

function AssetMetric({ label, value, tone }: AssetMetricProps) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p
        className={
          tone === "destructive" && value
            ? "text-xl font-semibold tracking-tight text-destructive tabular-nums"
            : "text-xl font-semibold tracking-tight tabular-nums"
        }
      >
        {value == null ? "—" : formatNumber(value)}
      </p>
    </div>
  );
}
