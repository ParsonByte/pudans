"use client";

import type { ReactNode } from "react";

import { Descriptions } from "tdesign-react";

type DescriptionGridItem = {
    label: ReactNode;
    value: ReactNode;
};

type DescriptionGridProps = {
    className?: string;
    columns?: 2 | 3 | 4;
    items: DescriptionGridItem[];
};

export function DescriptionGrid({ className, columns = 4, items }: DescriptionGridProps) {
    return (
        <Descriptions
            className={className}
            column={columns}
            itemLayout="vertical"
            items={items.map((item) => ({
                content: item.value,
                label: item.label,
            }))}
            size="small"
        />
    );
}
