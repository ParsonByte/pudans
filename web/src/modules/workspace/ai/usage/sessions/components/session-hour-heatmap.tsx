import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import type { UsageSessionStatsHour } from "../../lib/api";

const OPACITY_LEVELS = [0.2, 0.4, 0.65, 1];

export function SessionHourHeatmap({ hours }: { hours: UsageSessionStatsHour[] }) {
  const maxSessions = hours.reduce((max, hour) => Math.max(max, hour.sessions), 0);
  const total = hours.reduce((sum, hour) => sum + hour.sessions, 0);

  if (hours.length === 0 || total === 0) {
    return <ConsoleEmpty>所选时间范围内没有会话活动记录。</ConsoleEmpty>;
  }

  return (
    <div className="space-y-3 px-5 pb-5">
      <div className="overflow-x-auto">
        <div className="min-w-[480px] space-y-3">
          <div className="grid gap-1" style={{ gridTemplateColumns: "repeat(24, minmax(0, 1fr))" }}>
            {hours.map((hour) => {
              const ratio = maxSessions > 0 ? hour.sessions / maxSessions : 0;
              const level =
                hour.sessions <= 0
                  ? -1
                  : ratio <= 0.25
                    ? 0
                    : ratio <= 0.5
                      ? 1
                      : ratio <= 0.75
                        ? 2
                        : 3;
              return (
                <Tooltip key={hour.hour}>
                  <TooltipTrigger asChild>
                    <div
                      className="flex h-9 items-end justify-center rounded-sm bg-muted pt-1 text-[9px] text-muted-foreground"
                      style={
                        level < 0
                          ? undefined
                          : {
                              backgroundColor: "var(--chart-2)",
                              opacity: OPACITY_LEVELS[level],
                              color: level >= 2 ? "var(--background)" : "var(--muted-foreground)",
                            }
                      }
                    >
                      {hour.hour}
                    </div>
                  </TooltipTrigger>
                  <TooltipContent>
                    <p className="font-medium">
                      {String(hour.hour).padStart(2, "0")}:00 -{" "}
                      {String((hour.hour + 1) % 24).padStart(2, "0")}:00
                    </p>
                    <p>{formatNumber(hour.sessions)} 个会话</p>
                  </TooltipContent>
                </Tooltip>
              );
            })}
          </div>
          <div className="flex items-center justify-between text-[10px] text-muted-foreground">
            <span>0 时</span>
            <span className="flex items-center gap-1">
              少
              {OPACITY_LEVELS.map((opacity) => (
                <span
                  key={opacity}
                  className="size-2.5 rounded-sm"
                  style={{ backgroundColor: "var(--chart-2)", opacity }}
                />
              ))}
              多
            </span>
            <span>23 时</span>
          </div>
        </div>
      </div>
    </div>
  );
}
