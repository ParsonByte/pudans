"use client";

import type { ReactNode } from "react";

import { Card } from "tdesign-react";

import { useDesignTokens } from "./design-tokens";

type StatTileProps = {
    className?: string;
    compact?: boolean;
    label: ReactNode;
    value: ReactNode;
};

export function StatTile({ className, compact = false, label, value }: StatTileProps) {
    const token = useDesignTokens();

    return (
        <Card
            className={className}
            size="small"
            style={{ background: token.colorFillQuaternary, borderColor: token.colorBorderSecondary }}
        >
            <div className="text-[11px]" style={{ color: token.colorTextSecondary }}>
                {label}
            </div>
            <div
                className={`${compact ? "mt-0.5" : "mt-1"} text-sm font-semibold [overflow-wrap:anywhere] break-words`}
                style={{ color: token.colorText }}
            >
                {value}
            </div>
        </Card>
    );
}
