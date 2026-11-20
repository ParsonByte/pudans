import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { UsageCompositionBar } from "../../components/usage-composition-bar";
import type { UsageContextTotals } from "../../lib/api";
import { formatPercent } from "../../lib/model";
import { CONTEXT_PARTS } from "../lib/context";

type UsageContextCompositionProps = {
  totals: UsageContextTotals;
  scopeLabel: string;
};

export function UsageContextComposition({ totals, scopeLabel }: UsageContextCompositionProps) {
  const segments = CONTEXT_PARTS.map((part) => ({
    key: part.key,
    label: part.label,
    tokens: Math.max(totals[part.totalsKey] ?? 0, 0),
    color: part.color,
  }));
  const total = segments.reduce((sum, segment) => sum + segment.tokens, 0);

  return (
    <ConsoleSection className="overflow-hidden">
      <ConsoleSectionHeader
        title="上下文构成"
        description={`${scopeLabel} · 按系统提示、历史消息、记忆、知识库、用户输入与输出拆分 Token。`}
      />
      {total === 0 ? (
        <div className="space-y-4 p-5">
          <ConsoleEmpty>当前时间范围内暂无上下文 Token 记录。</ConsoleEmpty>
          <ContextEstimationNote />
        </div>
      ) : (
        <div className="space-y-5 p-5">
          <UsageCompositionBar segments={segments} />
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-0">组成</TableHead>
                <TableHead className="px-0">说明</TableHead>
                <TableHead className="px-0 text-end">Token</TableHead>
                <TableHead className="px-0 text-end">占比</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {CONTEXT_PARTS.map((part) => {
                const tokens = Math.max(totals[part.totalsKey] ?? 0, 0);
                return (
                  <TableRow key={part.key}>
                    <TableCell className="px-0 py-2.5">
                      <span className="inline-flex items-center gap-2 text-sm font-medium">
                        <span className="size-2 shrink-0" style={{ backgroundColor: part.color }} />
                        {part.label}
                      </span>
                    </TableCell>
                    <TableCell className="hidden px-0 py-2.5 text-xs text-muted-foreground md:table-cell">
                      {part.description}
                    </TableCell>
                    <TableCell className="px-0 py-2.5 text-end text-sm tabular-nums">
                      {formatTokenCount(tokens)}
                    </TableCell>
                    <TableCell className="px-0 py-2.5 text-end text-sm text-muted-foreground tabular-nums">
                      {formatPercent(total > 0 ? tokens / total : null)}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          <ContextEstimationNote />
        </div>
      )}
    </ConsoleSection>
  );
}

export function ContextEstimationNote() {
  return (
    <p className="text-xs leading-5 text-muted-foreground">
      估算方法：系统提示按 Agent
      提示词估算，记忆与知识库按实际注入片段统计，用户输入按消息字符估算；历史消息为输入 Token
      扣除系统、记忆、知识库与用户输入后的剩余。CJK 字符按 1 Token 计，其他字符按每 4 个字符 1 Token
      估算，因此构成数据为近似值。
    </p>
  );
}
