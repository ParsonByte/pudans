"use client";

import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import type { EChartsOption } from "echarts";
import { Loader2, RefreshCw } from "lucide-react";

import {
  chartAxisLabel,
  chartGrid,
  chartLegend,
  chartSplitLine,
  chartTooltip,
} from "@/components/charts/chart-options";
import { useChartTokens } from "@/components/charts/chart-theme";
import { EChart } from "@/components/charts/echart";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { ConsoleState } from "@/modules/workspace/console/components/console-state";

import { formatNumber, modelDisplayName } from "../../providers/lib/model";
import { type AiRoutingStatsRow, fetchRoutingStats } from "../lib/api";
import {
  type AiRoutingLookups,
  formatTokenCount,
  providerDisplayName,
  requestStatusLabel,
} from "../lib/model";

const RANGE_OPTIONS = [
  { value: "7", label: "近 7 天" },
  { value: "14", label: "近 14 天" },
  { value: "30", label: "近 30 天" },
  { value: "90", label: "近 90 天" },
] as const;

type RoutingBillingProps = {
  lookups: AiRoutingLookups;
};

export function RoutingBilling({ lookups }: RoutingBillingProps) {
  const [days, setDays] = useState("14");
  const query = useQuery({
    queryKey: ["ai-routing-stats", days],
    queryFn: () => fetchRoutingStats(Number(days)),
    retry: false,
    staleTime: 60_000,
  });
  const rows = useMemo(() => query.data?.rows ?? [], [query.data]);
  const sorted = useMemo(
    () =>
      [...rows].sort((left, right) => right.units - left.units || right.requests - left.requests),
    [rows],
  );
  const totals = useMemo(
    () =>
      rows.reduce(
        (result, row) => {
          result.requests += row.requests;
          result.units += row.units;
          if (row.status === "FAILED") {
            result.failed += row.requests;
          }
          result.providers.add(row.provider);
          return result;
        },
        { requests: 0, units: 0, failed: 0, providers: new Set<string>() },
      ),
    [rows],
  );

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="路由计费统计"
        description="按供应商、模型和请求状态聚合网关请求数与标准 Token 消耗。"
        action={
          <div className="flex flex-wrap items-center gap-2">
            <Select value={days} onValueChange={setDays}>
              <SelectTrigger className="h-8 w-32" aria-label="计费统计时间范围">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {RANGE_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void query.refetch()}
              disabled={query.isFetching}
            >
              {query.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          </div>
        }
      />

      {query.isError ? (
        <ConsoleState
          variant="error"
          title="无法加载路由计费统计"
          description="请检查登录状态或稍后重试。"
          onRetry={() => void query.refetch()}
        />
      ) : query.isPending ? (
        <div className="space-y-3 p-5">
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-[300px] w-full" />
        </div>
      ) : rows.length === 0 ? (
        <ConsoleEmpty>所选时间范围内没有路由计费记录。</ConsoleEmpty>
      ) : (
        <>
          <div className="grid gap-4 border-b p-5 sm:grid-cols-2 xl:grid-cols-4">
            <BillingMetric label="请求总数" value={formatNumber(totals.requests)} />
            <BillingMetric label="标准 Token" value={formatTokenCount(totals.units)} />
            <BillingMetric
              label="失败请求"
              value={formatNumber(totals.failed)}
              tone={totals.failed > 0 ? "destructive" : undefined}
            />
            <BillingMetric label="供应商" value={formatNumber(totals.providers.size)} />
          </div>
          <RoutingBillingChart rows={rows} lookups={lookups} />
          <div className="overflow-x-auto border-t">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-5">供应商</TableHead>
                  <TableHead className="px-5">模型</TableHead>
                  <TableHead className="px-5">状态</TableHead>
                  <TableHead className="px-5 text-end">请求</TableHead>
                  <TableHead className="px-5 text-end">标准 Token</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sorted.map((row) => (
                  <TableRow key={`${row.provider}-${row.model}-${row.status}`}>
                    <TableCell className="px-5 py-2.5 text-sm">
                      {providerDisplayName(lookups.providers.get(row.provider), row.provider)}
                    </TableCell>
                    <TableCell className="px-5 py-2.5 font-mono text-xs">
                      {modelName(row.model, lookups)}
                    </TableCell>
                    <TableCell className="px-5 py-2.5">
                      <Badge variant={row.status === "FAILED" ? "destructive" : "secondary"}>
                        {requestStatusLabel(row.status)}
                      </Badge>
                    </TableCell>
                    <TableCell className="px-5 py-2.5 text-end text-sm tabular-nums">
                      {formatNumber(row.requests)}
                    </TableCell>
                    <TableCell className="px-5 py-2.5 text-end text-sm tabular-nums">
                      {formatTokenCount(row.units)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}
    </ConsoleSection>
  );
}

type BillingMetricProps = {
  label: string;
  value: string;
  tone?: "destructive";
};

function BillingMetric({ label, value, tone }: BillingMetricProps) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p
        className={
          tone === "destructive"
            ? "text-xl font-semibold tracking-tight text-destructive tabular-nums"
            : "text-xl font-semibold tracking-tight tabular-nums"
        }
      >
        {value}
      </p>
    </div>
  );
}

type RoutingBillingChartProps = {
  rows: AiRoutingStatsRow[];
  lookups: AiRoutingLookups;
};

function RoutingBillingChart({ rows, lookups }: RoutingBillingChartProps) {
  const tokens = useChartTokens();
  const option = useMemo<EChartsOption>(() => {
    const byProvider = new Map<string, { success: number; failed: number; other: number }>();
    for (const row of rows) {
      const bucket = byProvider.get(row.provider) ?? { success: 0, failed: 0, other: 0 };
      if (row.status === "FAILED") {
        bucket.failed += row.units;
      } else if (row.status === "SUCCESS" || row.status === "SUCCEEDED") {
        bucket.success += row.units;
      } else {
        bucket.other += row.units;
      }
      byProvider.set(row.provider, bucket);
    }
    const providers = [...byProvider.entries()]
      .sort(
        ([, left], [, right]) =>
          right.success + right.failed + right.other - (left.success + left.failed + left.other),
      )
      .slice(0, 8);
    const labels = providers.map(([provider]) =>
      providerDisplayName(lookups.providers.get(provider), provider),
    );
    const hasOther = providers.some(([, bucket]) => bucket.other > 0);
    return {
      grid: { ...chartGrid(), bottom: 36 },
      tooltip: {
        ...chartTooltip(tokens),
        trigger: "axis",
        axisPointer: { type: "shadow" },
        formatter: (params) => {
          const items = Array.isArray(params) ? params : [params];
          const first = items[0];
          if (!first) {
            return "";
          }
          const entries = items.map(
            (item) =>
              `${item.marker ?? ""}${item.seriesName ?? ""}：${formatTokenCount(
                Number(item.value ?? 0),
              )}`,
          );
          return `${String(first.name ?? "")}<br/>${entries.join("<br/>")}`;
        },
      },
      legend: chartLegend(tokens),
      xAxis: {
        type: "category",
        data: labels,
        axisLabel: {
          ...chartAxisLabel(tokens),
          interval: 0,
          formatter: (value: string) => (value.length > 12 ? `${value.slice(0, 12)}…` : value),
        },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      yAxis: {
        type: "value",
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value) => formatTokenCount(value),
        },
        splitLine: chartSplitLine(tokens),
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: [
        {
          type: "bar",
          name: "成功",
          stack: "units",
          data: providers.map(([, bucket]) => bucket.success),
          itemStyle: { color: tokens.chart2 },
          barMaxWidth: 32,
        },
        {
          type: "bar",
          name: "失败",
          stack: "units",
          data: providers.map(([, bucket]) => bucket.failed),
          itemStyle: { color: tokens.destructive },
          barMaxWidth: 32,
        },
        ...(hasOther
          ? [
              {
                type: "bar" as const,
                name: "其他",
                stack: "units",
                data: providers.map(([, bucket]) => bucket.other),
                itemStyle: { color: tokens.mutedForeground },
                barMaxWidth: 32,
              },
            ]
          : []),
      ],
    };
  }, [lookups, rows, tokens]);

  return (
    <div className="h-[320px] w-full p-4 pt-5">
      <EChart option={option} height="100%" ariaLabel="按供应商统计的模型标准 Token 消耗" />
    </div>
  );
}

function modelName(model: string, lookups: AiRoutingLookups): string {
  const item = lookups.models.get(model);
  return item ? modelDisplayName(item) : model;
}
