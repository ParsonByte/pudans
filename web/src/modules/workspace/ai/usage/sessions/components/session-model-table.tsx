import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatPoints, formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import type { UsageSessionStatsModel } from "../../lib/api";

export function SessionModelTable({ models }: { models: UsageSessionStatsModel[] }) {
  const totalTokens = models.reduce((sum, model) => sum + model.tokens, 0);
  if (models.length === 0) {
    return <ConsoleEmpty>所选时间范围内没有模型分布数据。</ConsoleEmpty>;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="px-5">模型</TableHead>
          <TableHead className="px-5 text-end">会话数</TableHead>
          <TableHead className="px-5 text-end">Tokens</TableHead>
          <TableHead className="w-40 px-5">Token 占比</TableHead>
          <TableHead className="px-5 text-end">积分</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {models.map((model) => {
          const share = totalTokens > 0 ? model.tokens / totalTokens : 0;
          return (
            <TableRow key={model.name}>
              <TableCell className="px-5 py-3 text-sm">{model.name}</TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatNumber(model.sessions)}
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatTokenCount(model.tokens)}
              </TableCell>
              <TableCell className="px-5 py-3">
                <div className="flex items-center gap-2">
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-chart-2"
                      style={{ width: `${Math.max(share * 100, share > 0 ? 2 : 0)}%` }}
                    />
                  </div>
                  <span className="w-10 text-end text-[10px] text-muted-foreground tabular-nums">
                    {formatNumber(share * 100, 1)}%
                  </span>
                </div>
              </TableCell>
              <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                {formatPoints(model.points) ?? "—"}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
