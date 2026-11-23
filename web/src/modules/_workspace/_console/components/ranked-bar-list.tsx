"use client";

import type { ReactNode } from "react";

import { Empty, Progress, Tooltip } from "tdesign-react";

import { formatCount } from "../composables/_format";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";

type RankedBarItem = {
    key: string;
    label: ReactNode;
    labelTitle?: string;
    meta?: ReactNode;
    value: number;
    valueLabel: string;
};

type RankedBarListProps = {
    emptyText: string;
    items: RankedBarItem[];
};

export function RankedBarList({ emptyText, items }: RankedBarListProps) {
    const { token, tone } = useDashboardTheme();

    if (items.length === 0) {
        return <Empty description={emptyText} size="small" />;
    }

    const maximum = Math.max(1, ...items.map((item) => item.value));
    const rankColors = [tone("gold").base, tone("geekblue").base, tone("volcano").base, tone("blue").base];

    return (
        <div className="space-y-4">
            {items.map((item, index) => {
                const rankColor = rankColors[index] ?? token.colorPrimary;

                return (
                    <div key={item.key}>
                        <div className="flex items-center gap-3">
                            <span
                                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-xl text-xs font-semibold text-[var(--td-text-color-anti)]"
                                style={{ background: rankColor }}
                            >
                                {index + 1}
                            </span>
                            {item.labelTitle ? (
                                <Tooltip content={item.labelTitle}>
                                    <span
                                        className="min-w-0 flex-1 truncate text-sm font-medium"
                                        style={{ color: token.colorText }}
                                    >
                                        {item.label}
                                    </span>
                                </Tooltip>
                            ) : (
                                <span
                                    className="min-w-0 flex-1 truncate text-sm font-medium"
                                    style={{ color: token.colorText }}
                                >
                                    {item.label}
                                </span>
                            )}
                            <span className="text-xs tabular-nums" style={{ color: token.colorTextSecondary }}>
                                {formatCount(item.value)} {item.valueLabel}
                            </span>
                        </div>
                        <Progress
                            color={index < 3 ? rankColor : token.colorPrimaryBorder}
                            label={false}
                            percentage={item.value === 0 ? 0 : Math.max(3, Math.round((item.value / maximum) * 100))}
                            size="small"
                            trackColor={token.colorFillSecondary}
                        />
                        {item.meta ? (
                            <div
                                className="flex flex-wrap items-center gap-x-3 text-[11px]"
                                style={{ color: token.colorTextTertiary }}
                            >
                                {item.meta}
                            </div>
                        ) : null}
                    </div>
                );
            })}
        </div>
    );
}
