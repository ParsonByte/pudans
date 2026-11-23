"use client";

import { Fragment } from "react";

import { Empty, Tooltip } from "tdesign-react";

import { formatCount } from "../composables/_format";
import { type DashboardTone, useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";

type HeatmapCell = {
    hour?: number;
    weekday?: number;
};

type WeekHourHeatmapProps<T extends HeatmapCell> = {
    cells?: T[];
    count: (cell: T) => number | undefined;
    countUnit: string;
    emptyText: string;
    tone: DashboardTone;
    valueLabel: string;
};

const HOURS = Array.from({ length: 24 }, (_, index) => index);
const WEEKDAYS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
function heatmapOpacity(count: number, maximum: number): number {
    if (count <= 0) {
        return 1;
    }

    return 0.18 + Math.sqrt(count / maximum) * 0.82;
}

export function WeekHourHeatmap<T extends HeatmapCell>({
    cells,
    count,
    countUnit,
    emptyText,
    tone,
    valueLabel,
}: WeekHourHeatmapProps<T>) {
    const { token, tone: resolveTone } = useDashboardTheme();
    const data = cells ?? [];

    if (data.length === 0) {
        return <Empty description={emptyText} size="small" />;
    }

    const counts = new Map(data.map((cell) => [`${cell.weekday ?? 0}-${cell.hour ?? 0}`, count(cell) ?? 0]));
    const maximum = Math.max(1, ...data.map((cell) => count(cell) ?? 0));
    const heatColor = resolveTone(tone).base;

    return (
        <div>
            <div
                className="grid items-center gap-x-0.5 gap-y-1"
                style={{ gridTemplateColumns: "30px repeat(24, minmax(0, 1fr))" }}
            >
                <span />
                {HOURS.map((hour) => (
                    <span
                        aria-hidden={hour % 3 !== 0}
                        className="text-center text-[9px] tabular-nums sm:text-[10px]"
                        key={hour}
                        style={{ color: hour % 3 === 0 ? token.colorTextTertiary : "transparent" }}
                    >
                        {hour % 3 === 0 ? String(hour).padStart(2, "0") : "00"}
                    </span>
                ))}
                {WEEKDAYS.map((weekday, weekdayIndex) => (
                    <Fragment key={weekday}>
                        <span className="text-[10px] sm:text-[11px]" style={{ color: token.colorTextSecondary }}>
                            {weekday}
                        </span>
                        {HOURS.map((hour) => {
                            const cellCount = counts.get(`${weekdayIndex + 1}-${hour}`) ?? 0;
                            const fill = cellCount > 0 ? heatColor : token.colorFillQuaternary;

                            return (
                                <Tooltip
                                    content={`${weekday} ${String(hour).padStart(2, "0")}:00 · ${formatCount(cellCount)} ${countUnit}${valueLabel}`}
                                    key={`${weekday}-${hour}`}
                                >
                                    <div
                                        aria-label={`${weekday} ${String(hour).padStart(2, "0")}:00，${formatCount(cellCount)} ${countUnit}${valueLabel}`}
                                        className="h-4 min-w-0 rounded-[3px] transition-transform duration-150 hover:scale-110 sm:h-5"
                                        style={{
                                            background: fill,
                                            opacity: heatmapOpacity(cellCount, maximum),
                                        }}
                                    />
                                </Tooltip>
                            );
                        })}
                    </Fragment>
                ))}
            </div>
        </div>
    );
}
