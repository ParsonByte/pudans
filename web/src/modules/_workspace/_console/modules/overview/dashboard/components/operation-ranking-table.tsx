import { Empty, Table } from "tdesign-react";
import type { PrimaryTableCol } from "tdesign-react";

import { formatCount, formatPercent, operationLabel } from "../../../../composables/_format";

type OperationRow = {
    count: number;
    operation: string;
};

type OperationRankingTableProps = {
    items: OperationRow[];
    total: number;
};

export function OperationRankingTable({ items, total }: OperationRankingTableProps) {
    const columns: PrimaryTableCol<OperationRow>[] = [
        {
            align: "center",
            cell: ({ rowIndex }) => (
                <span
                    className={`inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold text-[var(--td-text-color-anti)] ${
                        rowIndex < 3 ? "bg-[var(--td-brand-color)]" : "bg-[var(--td-gray-color-5)]"
                    }`}
                >
                    {rowIndex + 1}
                </span>
            ),
            colKey: "rank",
            title: "排名",
            width: 72,
        },
        {
            cell: ({ row }) => operationLabel(row.operation),
            colKey: "operation",
            ellipsis: true,
            title: "操作",
        },
        {
            align: "center",
            cell: ({ row }) => formatCount(row.count),
            colKey: "count",
            title: "次数",
            width: 96,
        },
        {
            align: "center",
            cell: ({ row }) => formatPercent(row.count, total),
            colKey: "share",
            title: "占比",
            width: 88,
        },
    ];

    return (
        <Table<OperationRow>
            columns={columns}
            data={items}
            empty={<Empty description="暂无操作数据" size="small" />}
            hover
            rowKey="operation"
            size="medium"
            tableLayout="auto"
        />
    );
}
