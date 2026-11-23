"use client";

import type { AuditOperationStat } from "@pudans-dev/api-client";

import { formatPercent, operationLabel } from "../composables/_format";
import { RankedBarList } from "./ranked-bar-list";

type OperationRankingProps = {
    emptyText?: string;
    operations?: AuditOperationStat[];
    totalRecords?: number;
    valueLabel?: string;
};

export function OperationRanking({
    emptyText = "暂无操作分布",
    operations,
    totalRecords,
    valueLabel = "条",
}: OperationRankingProps) {
    const data = [...(operations ?? [])].sort((left, right) => (right.records ?? 0) - (left.records ?? 0));
    const topRecords = data.reduce((sum, item) => sum + (item.records ?? 0), 0);
    const total = totalRecords ?? topRecords;

    return (
        <RankedBarList
            emptyText={emptyText}
            items={data.map((item) => {
                const operation = item.operation ?? "";

                return {
                    key: operation || "unknown",
                    label: operationLabel(operation),
                    labelTitle: operation,
                    meta: <span>占总量 {formatPercent(item.records, total)}</span>,
                    value: item.records ?? 0,
                    valueLabel,
                };
            })}
        />
    );
}
