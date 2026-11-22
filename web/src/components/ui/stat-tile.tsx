"use client";

import type { ReactNode } from "react";

import { theme } from "antd";

type StatTileProps = {
    className?: string;
    compact?: boolean;
    label: ReactNode;
    value: ReactNode;
};

export function StatTile({ className, compact = false, label, value }: StatTileProps) {
    const { token } = theme.useToken();

    return (
        <div
            className={`rounded-2xl border ${compact ? "px-3 py-2.5" : "px-4 py-3"} ${className ?? ""}`}
            style={{
                background: token.colorFillQuaternary,
                borderColor: token.colorBorderSecondary,
            }}
        >
            <div className="text-[11px]" style={{ color: token.colorTextSecondary }}>
                {label}
            </div>
            <div
                className="mt-1 text-sm font-semibold [overflow-wrap:anywhere] break-words"
                style={{ color: token.colorText }}
            >
                {value}
            </div>
        </div>
    );
}
