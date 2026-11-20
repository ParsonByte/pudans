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
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import type { UsageContextBreakdown } from "../../lib/api";
import { formatPercent } from "../../lib/model";

const TABLE_COLUMNS = 6;

type UsageContextRankingsProps = {
  agents: UsageContextBreakdown[];
  models: UsageContextBreakdown[];
  modelFilter: string;
};

export function UsageContextRankings({ agents, models, modelFilter }: UsageContextRankingsProps) {
  if (modelFilter) {
    return (
      <ConsoleSection className="overflow-hidden">
        <ConsoleSectionHeader
          title="维度分布"
          description={`仅展示模型 ${modelFilter} 的上下文与窗口指标。`}
        />
        <ContextRankingTable
          rows={models.filter((model) => model.name === modelFilter)}
          nameLabel="模型"
        />
      </ConsoleSection>
    );
  }

  return (
    <ConsoleSection className="overflow-hidden">
      <Tabs defaultValue="agents">
        <ConsoleSectionHeader
          title="维度分布"
          description="按 Agent 与模型查看上下文构成与窗口利用率。"
          action={
            <TabsList>
              <TabsTrigger value="agents">Agent</TabsTrigger>
              <TabsTrigger value="models">模型</TabsTrigger>
            </TabsList>
          }
        />
        <TabsContent value="agents">
          <ContextRankingTable rows={agents} nameLabel="Agent" />
        </TabsContent>
        <TabsContent value="models">
          <ContextRankingTable rows={models} nameLabel="模型" />
        </TabsContent>
      </Tabs>
    </ConsoleSection>
  );
}

function ContextRankingTable({
  rows,
  nameLabel,
}: {
  rows: UsageContextBreakdown[];
  nameLabel: string;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="px-5">{nameLabel}</TableHead>
          <TableHead className="px-5 text-end">请求数</TableHead>
          <TableHead className="px-5 text-end">上下文 Token</TableHead>
          <TableHead className="px-5 text-end">输出 Token</TableHead>
          <TableHead className="px-5 text-end">窗口</TableHead>
          <TableHead className="px-5 text-end">窗口利用率</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.length === 0 ? (
          <TableRow>
            <TableCell colSpan={TABLE_COLUMNS}>
              <ConsoleEmpty>当前时间范围内没有{nameLabel}上下文数据。</ConsoleEmpty>
            </TableCell>
          </TableRow>
        ) : (
          rows.map((row) => (
            <TableRow key={row.name}>
              <TableCell className="px-5 py-3 text-sm font-medium break-all">{row.name}</TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatNumber(row.requests)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatTokenCount(row.inputTokens)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatTokenCount(row.outputTokens)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-xs text-muted-foreground tabular-nums">
                {formatTokenCount(row.contextWindow ?? undefined)}
              </TableCell>
              <TableCell className="px-5 py-3">
                <UtilizationCell value={row.windowUtilization} />
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}

function UtilizationCell({ value }: { value: number | null }) {
  if (value == null) {
    return <p className="text-end text-xs text-muted-foreground">—</p>;
  }
  return (
    <div className="flex items-center justify-end gap-2">
      <Progress
        value={Math.min(100, Math.max(0, value * 100))}
        className="h-1.5 w-20"
        aria-label="窗口利用率"
      />
      <span className="w-12 text-end text-xs tabular-nums">{formatPercent(value)}</span>
    </div>
  );
}
