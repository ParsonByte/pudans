import type { RecentOperation } from "@pudans-dev/api-client";
import { Empty, Table, Typography } from "tdesign-react";
import type { PrimaryTableCol } from "tdesign-react";

import { ResultTag } from "@/components/ui";

import { formatDateTime, operationLabel } from "../../../../composables/_format";
import { userLabel } from "../composables/_dashboard";

type RecentOperationRow = RecentOperation & {
    rowKey: string;
};

type RecentOperationsTableProps = {
    items: RecentOperation[];
};

export function RecentOperationsTable({ items }: RecentOperationsTableProps) {
    const rows: RecentOperationRow[] = items.map((record, index) => ({
        ...record,
        rowKey: record.auditId ?? `recent-${index}`,
    }));
    const columns: PrimaryTableCol<RecentOperationRow>[] = [
        {
            cell: ({ row }) => (
                <Typography.Text className="whitespace-nowrap tabular-nums" theme="secondary">
                    {formatDateTime(row.timestamp)}
                </Typography.Text>
            ),
            colKey: "timestamp",
            title: "时间",
            width: 168,
        },
        {
            cell: ({ row }) => <Typography.Text strong>{userLabel(row.operator)}</Typography.Text>,
            colKey: "operator",
            title: "操作人",
            width: 120,
        },
        {
            cell: ({ row }) => operationLabel(row.operation),
            colKey: "operation",
            title: "操作",
            width: 112,
        },
        {
            cell: ({ row }) => row.resourceName ?? row.resourceType ?? "—",
            colKey: "resource",
            ellipsis: true,
            title: "资源",
        },
        {
            align: "center",
            cell: ({ row }) => <ResultTag result={row.result ?? ""} />,
            colKey: "result",
            title: "结果",
            width: 88,
        },
    ];

    return (
        <Table<RecentOperationRow>
            columns={columns}
            data={rows}
            empty={<Empty description="暂无操作记录" size="small" />}
            hover
            rowKey="rowKey"
            size="medium"
            tableLayout="auto"
        />
    );
}
