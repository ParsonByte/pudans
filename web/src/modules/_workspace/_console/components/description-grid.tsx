"use client";

import type { Key, ReactNode } from "react";

import { theme } from "antd";

type DescriptionGridItem = {
    key?: Key;
    label: ReactNode;
    truncate?: boolean;
    value: ReactNode;
};

type DescriptionGridProps = {
    className?: string;
    columns?: 2 | 3 | 4;
    items: DescriptionGridItem[];
};

const COLUMN_CLASSES = {
    2: "sm:grid-cols-2",
    3: "sm:grid-cols-2 xl:grid-cols-3",
    4: "sm:grid-cols-2 xl:grid-cols-4",
} as const;

export function DescriptionGrid({ className, columns = 4, items }: DescriptionGridProps) {
    const { token } = theme.useToken();

    return (
        <dl className={`grid grid-cols-1 gap-x-6 gap-y-4 ${COLUMN_CLASSES[columns]} ${className ?? ""}`}>
            {items.map((item, index) => (
                <div className="min-w-0" key={item.key ?? index}>
                    <dt className="text-xs leading-5" style={{ color: token.colorTextTertiary }}>
                        {item.label}
                    </dt>
                    <dd
                        className={`mt-0.5 min-w-0 text-sm ${item.truncate === false ? "break-words" : "truncate"}`}
                        style={{ color: token.colorText }}
                    >
                        {item.value}
                    </dd>
                </div>
            ))}
        </dl>
    );
}
