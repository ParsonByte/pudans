import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import type { UsageContextTotals } from "../../lib/api";
import { formatPercent } from "../../lib/model";

type UsageContextWindowProps = {
  totals: UsageContextTotals;
  scopeLabel: string;
  contextWindow: number | null;
};

export function UsageContextWindow({ totals, scopeLabel, contextWindow }: UsageContextWindowProps) {
  const utilization = totals.windowUtilization;
  const hasWindowData = utilization != null || totals.contextWindowTokens != null;
  const progressValue = utilization == null ? 0 : Math.min(100, Math.max(0, utilization * 100));

  return (
    <ConsoleSection>
      <ConsoleSectionHeader title="窗口利用率" description={scopeLabel} />
      <div className="space-y-4 p-5">
        {hasWindowData ? (
          <>
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="text-3xl font-semibold tracking-tight tabular-nums">
                  {formatPercent(utilization)}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  按窗口容量加权的输入 Token 占比
                </p>
              </div>
              {contextWindow != null ? (
                <Badge variant="outline">配置窗口 {formatTokenCount(contextWindow)}</Badge>
              ) : null}
            </div>
            <Progress value={progressValue} aria-label="上下文窗口利用率" />
            <div className="border bg-muted/30 px-3 py-2.5">
              <p className="text-[11px] text-muted-foreground">窗口容量合计</p>
              <p className="mt-1 text-sm font-medium tabular-nums">
                {formatTokenCount(totals.contextWindowTokens ?? undefined)}
              </p>
            </div>
          </>
        ) : (
          <ConsoleEmpty>当前时间范围内的请求未配置上下文窗口，无法计算窗口利用率。</ConsoleEmpty>
        )}
        <p className="text-xs leading-5 text-muted-foreground">
          仅统计配置了上下文窗口的 Agent 与模型请求：窗口容量为窗口大小 ×
          请求数，利用率为这些请求的输入 Token 占窗口容量的比例。
        </p>
      </div>
    </ConsoleSection>
  );
}
