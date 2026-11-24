"use client";

import type { ReactNode } from "react";

import { Card, Skeleton } from "tdesign-react";

import { useDesignTokens } from "./design-tokens";

type MetricTone = {
    base: string;
    soft: string;
};

type MetricCardProps = {
    badge?: ReactNode;
    className?: string;
    footer?: ReactNode;
    icon?: ReactNode;
    label: ReactNode;
    loading?: boolean;
    tone?: MetricTone;
    value: ReactNode;
};

export function MetricCard({ badge, className, footer, icon, label, loading, tone, value }: MetricCardProps) {
    const token = useDesignTokens();

    return (
        <Card bordered={false} className={`relative h-full overflow-hidden ${className ?? ""}`}>
            <div className="flex flex-col gap-5">
                <div className="flex items-start justify-between gap-3">
                    <div className="flex min-w-0 items-center gap-3">
                        {icon ? (
                            <span
                                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl"
                                style={{
                                    backgroundColor: tone?.soft ?? token.colorFillSecondary,
                                    color: tone?.base ?? token.colorPrimary,
                                }}
                            >
                                {icon}
                            </span>
                        ) : null}
                        <span className="text-sm font-medium" style={{ color: token.colorTextSecondary }}>
                            {label}
                        </span>
                    </div>
                    {badge}
                </div>
                <div>
                    {loading ? (
                        <Skeleton animation="gradient" rowCol={[1]} />
                    ) : (
                        <div className="text-3xl font-semibold tracking-tight" style={{ color: token.colorText }}>
                            {value}
                        </div>
                    )}
                    {footer ? <div className="mt-2 flex items-center gap-1 text-xs font-medium">{footer}</div> : null}
                </div>
            </div>
        </Card>
    );
}
