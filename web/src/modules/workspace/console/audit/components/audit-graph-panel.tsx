"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import type { EChartsOption } from "echarts";
import { Loader2, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { serverToday } from "@/lib/date";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";

import {
  type AuditGraphHourPoint,
  auditGraphHourly,
  auditGraphOperationCounts,
  fetchAuditGraph,
  formatAuditHour,
} from "../lib/api";
import { auditOperationLabel } from "../lib/audit-labels";

type AuditGraphPanelProps = {
  suggestedUid?: string;
};

export function AuditGraphPanel({ suggestedUid = "" }: AuditGraphPanelProps) {
  const today = serverToday();
  const [uidDraft, setUidDraft] = useState("");
  const [dateDraft, setDateDraft] = useState(today);
  const [applied, setApplied] = useState<{ uid: string; date: string } | null>(null);
  const touchedRef = useRef(false);

  useEffect(() => {
    if (!touchedRef.current && suggestedUid) {
      setUidDraft(suggestedUid);
    }
  }, [suggestedUid]);

  const uid = applied?.uid ?? suggestedUid.trim();
  const date = applied?.date ?? today;
  const query = useQuery({
    queryKey: ["audit-graph", uid, date],
    queryFn: () => fetchAuditGraph(uid, date),
    enabled: Boolean(uid && date),
    retry: false,
    staleTime: 30_000,
  });

  const nodes = useMemo(() => query.data?.nodes ?? [], [query.data]);
  const hourly = useMemo(() => auditGraphHourly(nodes), [nodes]);
  const operations = useMemo(() => auditGraphOperationCounts(nodes).slice(0, 8), [nodes]);
  const traceFlowCount = useMemo(
    () => new Set(nodes.map((node) => node.traceFlowId).filter(Boolean)).size,
    [nodes],
  );

  const apply = () => {
    const nextUid = uidDraft.trim();
    if (!nextUid) {
      toast.error("请输入用户 UID。");
      return;
    }
    touchedRef.current = true;
    setApplied({ uid: nextUid, date: dateDraft || today });
  };

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="用户操作图谱"
        description="选择用户与日期，查看当日审计节点之间的父子关系、链路分布和每小时活动趋势。"
        action={<Badge variant="outline">北京时间自然日</Badge>}
      />
      <div className="grid gap-4 border-b p-5 sm:grid-cols-2 xl:grid-cols-4">
        <div className="space-y-2">
          <Label htmlFor="audit-graph-uid">用户 UID</Label>
          <Input
            id="audit-graph-uid"
            value={uidDraft}
            placeholder="输入用户 UID"
            onChange={(event) => {
              touchedRef.current = true;
              setUidDraft(event.target.value);
            }}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="audit-graph-date">日期</Label>
          <Input
            id="audit-graph-date"
            type="date"
            max={today}
            value={dateDraft}
            onChange={(event) => setDateDraft(event.target.value)}
          />
        </div>
        <div className="flex items-end gap-2">
          <Button type="button" onClick={apply} disabled={query.isFetching}>
            {query.isFetching ? <Loader2 className="animate-spin" /> : <Search />}
            查询
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => void query.refetch()}
            disabled={query.isFetching || !uid}
          >
            <RefreshCw />
            刷新
          </Button>
        </div>
      </div>

      {!uid ? (
        <ConsoleState variant="empty" title="输入用户 UID 查看当日操作图谱。" />
      ) : query.isError ? (
        <ConsoleState
          variant="error"
          title="无法加载用户操作图谱"
          description="请检查用户 UID、日期或登录状态后重试。"
          onRetry={() => void query.refetch()}
        />
      ) : query.isPending ? (
        <ConsoleBlockSkeleton rows={2} className="space-y-3 p-5" itemClassName="h-28 w-full" />
      ) : nodes.length === 0 ? (
        <ConsoleState variant="empty" title="该用户当日没有审计记录。" />
      ) : (
        <>
          <div className="grid gap-4 border-b p-5 sm:grid-cols-2 xl:grid-cols-4">
            <GraphMetric label="审计节点" value={nodes.length} />
            <GraphMetric label="父子关系" value={query.data?.edges?.length ?? 0} />
            <GraphMetric label="根节点" value={query.data?.roots?.length ?? 0} />
            <GraphMetric label="追踪流" value={traceFlowCount} />
          </div>
          <AuditGraphTrend hourly={hourly} />
          {operations.length > 0 ? (
            <div className="border-t p-5">
              <p className="text-xs font-medium text-muted-foreground">高频操作</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {operations.map((entry) => (
                  <Badge key={entry.operation} variant="secondary">
                    {auditOperationLabel(entry.operation)}
                    <span className="text-muted-foreground tabular-nums">{entry.count}</span>
                  </Badge>
                ))}
              </div>
            </div>
          ) : null}
        </>
      )}
    </ConsoleSection>
  );
}

function GraphMetric({ label, value }: { label: string; value: number }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="text-xl font-semibold tracking-tight tabular-nums">
        {value.toLocaleString("zh-CN")}
      </p>
    </div>
  );
}

type AuditGraphTrendProps = {
  hourly: AuditGraphHourPoint[];
};

function AuditGraphTrend({ hourly }: AuditGraphTrendProps) {
  const tokens = useChartTokens();
  const hasOther = hourly.some((point) => point.other > 0);
  const option = useMemo<EChartsOption>(() => {
    const series: EChartsOption["series"] = [
      {
        type: "bar",
        name: "成功",
        stack: "events",
        data: hourly.map((point) => point.success),
        itemStyle: { color: tokens.chart2 },
        barMaxWidth: 24,
      },
      {
        type: "bar",
        name: "失败/拒绝",
        stack: "events",
        data: hourly.map((point) => point.failed),
        itemStyle: { color: tokens.destructive },
        barMaxWidth: 24,
      },
      ...(hasOther
        ? [
            {
              type: "bar" as const,
              name: "其他",
              stack: "events",
              data: hourly.map((point) => point.other),
              itemStyle: { color: tokens.mutedForeground },
              barMaxWidth: 24,
            },
          ]
        : []),
    ];
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
          const rows = items.map(
            (item) =>
              `${item.marker ?? ""}${item.seriesName ?? ""}：${Number(
                item.value ?? 0,
              ).toLocaleString("zh-CN")}`,
          );
          return `${formatAuditHour(String(first.name ?? ""))}<br/>${rows.join("<br/>")}`;
        },
      },
      legend: chartLegend(tokens),
      xAxis: {
        type: "category",
        data: hourly.map((point) => point.hour),
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value: string) => formatAuditHour(value),
        },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: chartAxisLabel(tokens),
        splitLine: chartSplitLine(tokens),
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series,
    };
  }, [hasOther, hourly, tokens]);

  return (
    <div className="h-[280px] w-full p-4 pt-5">
      <EChart option={option} height="100%" ariaLabel="用户当日审计事件趋势" />
    </div>
  );
}
