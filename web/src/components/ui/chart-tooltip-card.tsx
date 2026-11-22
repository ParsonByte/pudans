"use client";

import type { ReactNode } from "react";

import { theme } from "antd";

type ChartTooltipItem = {
    color?: string;
    label: ReactNode;
    value: ReactNode;
};

type ChartTooltipCardProps = {
    className?: string;
    items: ChartTooltipItem[];
    title?: ReactNode;
};

export function ChartTooltipCard({ className, items, title }: ChartTooltipCardProps) {
    const { token } = theme.useToken();

    return (
        <div
            className={`rounded-2xl border px-4 py-3 backdrop-blur ${className ?? ""}`}
            style={{
                background: token.colorBgElevated,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowSecondary,
            }}
        >
            {title ? (
                <div className="mb-2 text-xs font-medium" style={{ color: token.colorTextSecondary }}>
                    {title}
                </div>
            ) : null}
            <div className="space-y-1.5">
                {items.map((item, index) => (
                    <div className="flex min-w-36 items-center justify-between gap-6" key={index}>
                        <span className="flex items-center gap-2 text-xs" style={{ color: token.colorTextSecondary }}>
                            {item.color ? (
                                <span className="h-2 w-2 rounded-full" style={{ backgroundColor: item.color }} />
                            ) : null}
                            {item.label}
                        </span>
                        <span className="text-xs font-semibold" style={{ color: token.colorText }}>
                            {item.value}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}
