import { Link } from "@tanstack/react-router";
import { CircleDollarSign } from "lucide-react";

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
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import {
  formatPoints,
  formatTokenCount,
  tierLabel,
} from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import type { AiModelItem } from "../../../providers/lib/api";
import type { UsageRankingEntry, UsageRankings } from "../../lib/api";
import { formatPercent } from "../../lib/model";

const OTHER_SHARE_THRESHOLD = 0.01;

const OTHER_NAME = "其他（<1%）";

type UsageRankingsTableProps = {
  rankings: UsageRankings;
  totalTokens: number;
  models: AiModelItem[];
  providersPath?: string;
  modelsLoading?: boolean;
  modelsError?: boolean;
};

export function UsageRankingsTable({
  rankings,
  totalTokens,
  models,
  providersPath,
  modelsLoading = false,
  modelsError = false,
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
          modelsLoading={modelsLoading}
          modelsError={modelsError}
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
  modelsLoading?: boolean;
  modelsError?: boolean;
};

function RankingTable({
  rows,
  totalTokens,
  models,
  providersPath,
  modelsLoading = false,
  modelsError = false,
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
          <TableHead className="px-5 text-end">积分</TableHead>
          {models ? <TableHead className="px-5 text-end">价格</TableHead> : null}
        </TableRow>
      </TableHeader>
      <TableBody>
        {merged.map((row, index) => {
          const tokens = row.inputTokens + row.cacheTokens + row.outputTokens;
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
                {formatPoints(row.points) ?? "—"}
              </TableCell>
              {models ? (
                <TableCell className="px-5 py-3 text-end">
                  {row.name === OTHER_NAME ? (
                    <span className="text-xs text-muted-foreground">—</span>
                  ) : (
                    <ModelPricePopover
                      row={row}
                      models={models}
                      providersPath={providersPath}
                      loading={modelsLoading}
                      failed={modelsError}
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
    const tokens = row.inputTokens + row.cacheTokens + row.outputTokens;
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
      points: accumulator.points + row.points,
    }),
    {
      name: OTHER_NAME,
      provider: null,
      requests: 0,
      failed: 0,
      inputTokens: 0,
      cacheTokens: 0,
      outputTokens: 0,
      points: 0,
    },
  );
  return [...kept, other];
}

type ModelPricePopoverProps = {
  row: UsageRankingEntry;
  models: AiModelItem[];
  providersPath?: string;
  loading: boolean;
  failed: boolean;
};

function ModelPricePopover({
  row,
  models,
  providersPath,
  loading,
  failed,
}: ModelPricePopoverProps) {
  const matches = models.filter(
    (model) =>
      model.spec.modelCode === row.name &&
      (row.provider == null || model.spec.provider === row.provider),
  );

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="sm" className="h-7 px-2">
          <CircleDollarSign />
          价格
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80">
        <div className="space-y-3">
          <div>
            <p className="text-sm font-medium">{row.name}</p>
            <p className="text-[11px] text-muted-foreground">
              {row.provider ?? "全部供应商"} · 只读模型定价
            </p>
          </div>
          {loading ? (
            <p className="text-xs text-muted-foreground">正在加载模型定价配置…</p>
          ) : failed ? (
            <p className="text-xs text-destructive">无法加载模型定价配置，请稍后重试。</p>
          ) : matches.length === 0 ? (
            <p className="text-xs text-muted-foreground">
              未找到该模型的供应商模型配置，无法读取积分单价。
            </p>
          ) : (
            <div className="space-y-3">
              {matches.map((model) => (
                <div key={model.metadata.name} className="space-y-1.5 rounded-lg border p-2.5">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <Badge variant="outline">{model.spec.provider}</Badge>
                    <span className="font-mono text-[11px] text-muted-foreground">
                      {model.metadata.name}
                    </span>
                    {model.spec.tier ? (
                      <Badge variant="secondary">
                        {tierLabel(model.spec.tier) ?? model.spec.tier}
                      </Badge>
                    ) : null}
                  </div>
                  <PriceRow label="输入" value={model.spec.inputTokensPerPoint} />
                  <PriceRow label="缓存" value={model.spec.cacheTokensPerPoint} />
                  <PriceRow label="输出" value={model.spec.outputTokensPerPoint} />
                  <p className="text-[11px] text-muted-foreground">
                    上下文窗口{" "}
                    {model.spec.contextWindow == null
                      ? "未配置"
                      : formatTokenCount(model.spec.contextWindow)}
                  </p>
                </div>
              ))}
            </div>
          )}
          {providersPath ? (
            <Button asChild variant="outline" size="sm" className="w-full">
              <Link to={providersPath}>前往供应商模型编辑</Link>
            </Button>
          ) : null}
        </div>
      </PopoverContent>
    </Popover>
  );
}

function PriceRow({ label, value }: { label: string; value: number | undefined }) {
  return (
    <div className="flex items-center justify-between text-xs">
      <span className="text-muted-foreground">{label}（Token/积分）</span>
      <span className="font-medium tabular-nums">
        {value == null ? "未配置" : formatNumber(value, 0)}
      </span>
    </div>
  );
}
