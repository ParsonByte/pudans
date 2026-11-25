"use client";

import type { ReactNode } from "react";

import { Typography } from "tdesign-react";
import type { PaginationProps, TableProps } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";
import { useMobile } from "@/hooks/ui/use-mobile";

import { ConsoleTable } from "../../../../components/console-table";
import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { formatBytes, formatDateTime } from "../../../../composables/_format";
import type { StorageFile } from "../../../../composables/_storage";
import type { StorageFilesTab } from "../composables/_storage-files";
import { storageFileName } from "../composables/_storage-files";

type StorageFilesTableProps = {
    error?: unknown;
    files: StorageFile[];
    loading: boolean;
    onRetry?: () => void;
    pagination: PaginationProps | false;
    renderActions: (file: StorageFile) => ReactNode;
    tab: StorageFilesTab;
};

export function StorageFilesTable({
    error,
    files,
    loading,
    onRetry,
    pagination,
    renderActions,
    tab,
}: StorageFilesTableProps) {
    const token = useDesignTokens();
    const isMobile = useMobile();
    const columns: TableProps<StorageFile>["columns"] = [
        {
            cell: ({ row }) => {
                const displayName = storageFileName(row);

                return (
                    <div className={`flex min-w-0 flex-col gap-0.5 ${isMobile ? "max-w-[160px]" : "max-w-[260px]"}`}>
                        <Typography.Text ellipsis={{ row: 1, tooltipProps: { content: displayName } }} strong>
                            {displayName}
                        </Typography.Text>
                        <CopyableIdentifier className="text-[11px]" value={row.metadata.name} />
                    </div>
                );
            },
            colKey: "file",
            title: "文件",
            width: isMobile ? 160 : 260,
        },
        {
            cell: ({ row }) => <span className="whitespace-nowrap tabular-nums">{formatBytes(row.spec.size)}</span>,
            colKey: "size",
            title: "大小",
            width: isMobile ? 80 : 100,
        },
    ];

    if (!isMobile) {
        columns.push(
            {
                cell: ({ row }) => (
                    <span className="block max-w-[200px] truncate" style={{ color: token.colorTextSecondary }}>
                        {row.spec.policy?.trim() || "—"}
                    </span>
                ),
                colKey: "policy",
                title: "存储策略",
                width: 200,
            },
            {
                cell: ({ row }) => (
                    <span className="whitespace-nowrap tabular-nums">{formatDateTime(row.spec.uploadedAt)}</span>
                ),
                colKey: "uploadedAt",
                title: "上传时间",
                width: 180,
            },
        );
    }

    if (tab === "trashed" && !isMobile) {
        columns.push(
            {
                cell: ({ row }) => (
                    <span className="whitespace-nowrap tabular-nums">{formatDateTime(row.spec.trashedAt)}</span>
                ),
                colKey: "trashedAt",
                title: "移入回收站",
                width: 180,
            },
            {
                cell: ({ row }) => (
                    <span className="whitespace-nowrap tabular-nums" style={{ color: token.colorWarning }}>
                        {formatDateTime(row.spec.trashExpiresAt)}
                    </span>
                ),
                colKey: "trashExpiresAt",
                title: "过期时间",
                width: 180,
            },
        );
    }

    columns.push({
        align: "right",
        cell: ({ row }) => renderActions(row),
        colKey: "actions",
        fixed: isMobile ? undefined : "right",
        title: "操作",
        width: isMobile ? 56 : 72,
    });

    return (
        <ConsoleTable
            cardless
            columns={columns}
            data={files}
            error={error}
            loading={loading}
            onRetry={onRetry}
            pagination={pagination}
            rowKey="metadata.name"
            tableProps={{ hover: true }}
        />
    );
}
