import { Scale } from "lucide-react";
import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Progress } from "@/components/ui/progress";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DEFAULT_TIER_MULTIPLIERS, billingRatios } from "@/modules/workspace/ai/plans/lib/model";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount, tierLabel } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import type { AiBilling, AiModelItem } from "../../../providers/lib/api";
import type { UsageRankingEntry, UsageRankings } from "../../lib/api";
import { formatPercent } from "../../lib/model";

const OTHER_SHARE_THRESHOLD = 0.01;

const OTHER_NAME = "其他（<1%）";

type UsageRankingsTableProps = {
  rankings: UsageRankings;
  totalTokens: number;
  models: AiModelItem[];
  providersPath?: string;
  plansPath?: string;
  modelsLoading?: boolean;
  modelsError?: boolean;
  billing?: AiBilling;
  billingLoading?: boolean;
  billingError?: boolean;
};

export function UsageRankingsTable({
  rankings,
  totalTokens,
  models,
  providersPath,
  plansPath,
  modelsLoading = false,
  modelsError = false,
  billing,
  billingLoading = false,
  billingError = false,
}: UsageRankingsTableProps) {
  return (
    <Tabs defaultValue="agents" className="gap-0">
      <div className="border-b px-5 py-3">
        <TabsList>
          <TabsTrigger value="agents">Agent</TabsTrigger>
          <TabsTrigger value="models">模型</TabsTrigger>
          <TabsTrigger value="providers">供应商</TabsTrigger>
          <TabsTrigger value="accounts">账号</TabsTrigger>
        </TabsList>
      </div>
      <TabsContent value="agents">
        <RankingTable rows={rankings.agents} totalTokens={totalTokens} />
      </TabsContent>
      <TabsContent value="models">
        <RankingTable
          rows={rankings.models}
          totalTokens={totalTokens}
          models={models}
          providersPath={providersPath}
          plansPath={plansPath}
          modelsLoading={modelsLoading}
          modelsError={modelsError}
          billing={billing}
          billingLoading={billingLoading}
          billingError={billingError}
        />
      </TabsContent>
      <TabsContent value="providers">
        <RankingTable rows={rankings.providers} totalTokens={totalTokens} />
      </TabsContent>
      <TabsContent value="accounts">
        <RankingTable rows={rankings.accounts} totalTokens={totalTokens} />
      </TabsContent>
    </Tabs>
  );
}

type RankingTableProps = {
  rows: UsageRankingEntry[];
  totalTokens: number;
  models?: AiModelItem[];
  providersPath?: string;
  plansPath?: string;
  modelsLoading?: boolean;
  modelsError?: boolean;
  billing?: AiBilling;
  billingLoading?: boolean;
  billingError?: boolean;
};

function RankingTable({
  rows,
  totalTokens,
  models,
  providersPath,
  plansPath,
  modelsLoading = false,
  modelsError = false,
  billing,
  billingLoading = false,
  billingError = false,
}: RankingTableProps) {
  if (rows.length === 0) {
    return <ConsoleEmpty>所选时间范围内暂无排行数据。</ConsoleEmpty>;
  }
  const merged = mergeSmallRows(rows, totalTokens);
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-12 px-5">#</TableHead>
          <TableHead className="px-5">名称</TableHead>
          <TableHead className="px-5">Token 占比</TableHead>
          <TableHead className="px-5 text-end">请求</TableHead>
          <TableHead className="px-5 text-end">失败</TableHead>
          <TableHead className="px-5 text-end">输入</TableHead>
          <TableHead className="px-5 text-end">缓存</TableHead>
          <TableHead className="px-5 text-end">输出</TableHead>
          <TableHead className="px-5 text-end">标准 Token</TableHead>
          {models ? <TableHead className="px-5 text-end">权重</TableHead> : null}
        </TableRow>
      </TableHeader>
      <TableBody>
        {merged.map((row, index) => {
          const tokens = row.inputTokens + row.outputTokens;
          const share = totalTokens > 0 ? tokens / totalTokens : 0;
          return (
            <TableRow key={`${row.provider ?? ""}-${row.name}-${index}`}>
              <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
                {row.name === OTHER_NAME ? "—" : index + 1}
              </TableCell>
              <TableCell className="px-5 py-3">
                <p className="text-sm font-medium">{row.name || "未知"}</p>
                {row.provider ? (
                  <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">
                    {row.provider}
                  </p>
                ) : null}
              </TableCell>
              <TableCell className="px-5 py-3">
                <div className="flex items-center gap-2">
                  <Progress value={Math.min(share * 100, 100)} className="h-1.5 w-24" />
                  <span className="w-12 text-xs text-muted-foreground tabular-nums">
                    {formatPercent(share)}
                  </span>
                </div>
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatNumber(row.requests)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatNumber(row.failed)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatTokenCount(row.inputTokens)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatTokenCount(row.cacheTokens)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatTokenCount(row.outputTokens)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatTokenCount(row.units)}
              </TableCell>
              {models ? (
                <TableCell className="px-5 py-3 text-end">
                  {row.name === OTHER_NAME ? (
                    <span className="text-xs text-muted-foreground">—</span>
                  ) : (
                    <ModelWeightPopover
                      row={row}
                      models={models}
                      providersPath={providersPath}
                      plansPath={plansPath}
                      loading={modelsLoading || billingLoading}
                      failed={modelsError || billingError}
                      billing={billing}
                    />
                  )}
                </TableCell>
              ) : null}
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

function mergeSmallRows(rows: UsageRankingEntry[], totalTokens: number): UsageRankingEntry[] {
  if (totalTokens <= 0 || rows.length < 2) {
    return rows;
  }
  const small = rows.filter((row) => {
    const tokens = row.inputTokens + row.outputTokens;
    return tokens / totalTokens < OTHER_SHARE_THRESHOLD;
  });
  if (small.length < 2) {
    return rows;
  }
  const kept = rows.filter((row) => !small.includes(row));
  const other = small.reduce<UsageRankingEntry>(
    (accumulator, row) => ({
      name: OTHER_NAME,
      provider: null,
      requests: accumulator.requests + row.requests,
      failed: accumulator.failed + row.failed,
      inputTokens: accumulator.inputTokens + row.inputTokens,
      cacheTokens: accumulator.cacheTokens + row.cacheTokens,
      outputTokens: accumulator.outputTokens + row.outputTokens,
      units: accumulator.units + row.units,
    }),
    {
      name: OTHER_NAME,
      provider: null,
      requests: 0,
      failed: 0,
      inputTokens: 0,
      cacheTokens: 0,
      outputTokens: 0,
      units: 0,
    },
  );
  return [...kept, other];
}

type ModelWeightPopoverProps = {
  row: UsageRankingEntry;
  models: AiModelItem[];
  providersPath?: string;
  plansPath?: string;
  loading: boolean;
  failed: boolean;
  billing?: AiBilling;
};

function ModelWeightPopover({
  row,
  models,
  providersPath,
  plansPath,
  loading,
  failed,
  billing,
}: ModelWeightPopoverProps) {
  const matches = models.filter(
    (model) =>
      (model.spec.modelCode === row.name || model.metadata.name === row.name) &&
      (row.provider == null || model.spec.provider === row.provider),
  );

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="sm" className="h-7 px-2">
          <Scale />
          权重
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-96">
        <div className="space-y-3">
          <div>
            <p className="text-sm font-medium">{row.name}</p>
            <p className="text-[11px] text-muted-foreground">
              {row.provider ?? "全部供应商"} · 只读权重系数
            </p>
          </div>
          {loading ? (
            <p className="text-xs text-muted-foreground">正在加载计费权重配置…</p>
          ) : failed ? (
            <p className="text-xs text-destructive">无法加载计费权重配置，请稍后重试。</p>
          ) : matches.length === 0 ? (
            <p className="text-xs text-muted-foreground">
              未找到该模型的供应商模型配置，无法读取权重系数。
            </p>
          ) : (
            <div className="space-y-3">
              {matches.map((model) => (
                <ModelWeightCard key={model.metadata.name} model={model} billing={billing} />
              ))}
            </div>
          )}
          <div className="grid grid-cols-2 gap-2">
            {plansPath ? (
              <Button asChild variant="outline" size="sm">
                <Link href={plansPath}>前往计费比例</Link>
              </Button>
            ) : null}
            {providersPath ? (
              <Button asChild variant="outline" size="sm">
                <Link href={providersPath}>前往模型配置</Link>
              </Button>
            ) : null}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

function ModelWeightCard({ model, billing }: { model: AiModelItem; billing?: AiBilling }) {
  const ratios = billingRatios(billing);
  const tierMultiplier = model.spec.tier
    ? (billing?.tierMultipliers?.[model.spec.tier] ?? DEFAULT_TIER_MULTIPLIERS[model.spec.tier])
    : 1;
  const modelMultiplier = billing?.modelMultipliers?.[model.spec.modelCode] ?? 1;
  const combined = tierMultiplier * modelMultiplier;

  return (
    <div className="space-y-1.5 border p-2.5">
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge variant="outline">{model.spec.provider}</Badge>
        <span className="font-mono text-[11px] text-muted-foreground">{model.metadata.name}</span>
        {model.spec.tier ? (
          <Badge variant="secondary">{tierLabel(model.spec.tier) ?? model.spec.tier}</Badge>
        ) : null}
      </div>
      <WeightRow
        label="档位系数"
        value={tierMultiplier}
        hint={model.spec.tier ? (tierLabel(model.spec.tier) ?? model.spec.tier) : "未设置档位"}
      />
      <WeightRow
        label="模型系数"
        value={modelMultiplier}
        hint={modelMultiplier === 1 ? "未覆盖，默认 1" : "计费比例中的模型覆盖"}
      />
      <WeightRow label="综合系数" value={combined} hint="档位系数 × 模型系数" />
      <div className="border-t pt-1.5">
        <p className="mb-1 text-[11px] font-medium text-muted-foreground">基础权重</p>
        <WeightRow label="输入" value={ratios.input} />
        <WeightRow label="输出" value={ratios.output} />
        <WeightRow label="缓存读取" value={ratios.cacheRead} />
        <WeightRow label="缓存写入" value={ratios.cacheWrite} />
      </div>
    </div>
  );
}

function WeightRow({
  label,
  value,
  hint,
}: {
  label: string;
  value: number | undefined;
  hint?: string;
}) {
  return (
    <div className="flex items-center justify-between gap-3 text-xs">
      <span className="text-muted-foreground">
        {label}
        {hint ? <span className="ms-1 text-[10px]">{hint}</span> : null}
      </span>
      <span className="font-medium tabular-nums">
        {value == null || !Number.isFinite(value) ? "—" : `×${formatNumber(value, 4)}`}
      </span>
    </div>
  );
}
