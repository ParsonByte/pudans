"use client";

import type { ReactNode } from "react";

import { Card } from "tdesign-react";

import { StatTile } from "@/components/ui";

export type ConsoleOverviewMetric = {
    label: ReactNode;
    value: ReactNode;
};

type ConsolePageOverviewProps = {
    actions?: ReactNode;
    icon: ReactNode;
    metrics: ConsoleOverviewMetric[];
    title: ReactNode;
};

export function ConsolePageOverview({ actions, icon, metrics, title }: ConsolePageOverviewProps) {
    return (
        <Card className="overflow-hidden" shadow>
            <div className="flex flex-col gap-5 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex min-w-0 items-start gap-3">
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                        {icon}
                    </span>
                    <h2 className="app-page__title min-w-0">{title}</h2>
                </div>
                {actions ? (
                    <div className="flex shrink-0 flex-col gap-2 sm:flex-row sm:items-center">{actions}</div>
                ) : null}
            </div>
            {metrics.length > 0 ? (
                <div className="relative mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
                    {metrics.map((metric, index) => (
                        <StatTile compact key={index} label={metric.label} value={metric.value} />
                    ))}
                </div>
            ) : null}
        </Card>
    );
}
