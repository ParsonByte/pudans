import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";

import { formatPercent } from "../lib/model";

type UsageCompositionSegment = {
  key: string;
  label: string;
  tokens: number;
  color: string;
};

type UsageCompositionBarProps = {
  segments: UsageCompositionSegment[];
};

export function UsageCompositionBar({ segments }: UsageCompositionBarProps) {
  const total = segments.reduce((sum, segment) => sum + Math.max(segment.tokens, 0), 0);

  return (
    <div className="space-y-3">
      <div className="flex h-3 w-full overflow-hidden rounded-full bg-muted">
        {total > 0
          ? segments
              .filter((segment) => segment.tokens > 0)
              .map((segment) => (
                <div
                  key={segment.key}
                  className="h-full"
                  style={{
                    width: `${(segment.tokens / total) * 100}%`,
                    backgroundColor: segment.color,
                  }}
                  title={`${segment.label}：${formatTokenCount(segment.tokens)}`}
                />
              ))
          : null}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-2 text-xs text-muted-foreground">
        {segments.map((segment) => (
          <span key={segment.key} className="inline-flex items-center gap-1.5">
            <span
              className="size-2 shrink-0 rounded-[2px]"
              style={{ backgroundColor: segment.color }}
            />
            {segment.label}
            <span className="tabular-nums">
              {formatPercent(total > 0 ? segment.tokens / total : null)}
            </span>
          </span>
        ))}
      </div>
    </div>
  );
}
