"use client";

import { type ReactNode, useEffect } from "react";

import { Button, Card, Empty, Table } from "tdesign-react";
import type { PaginationProps, TableProps, TableRowData } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../composables/_errors";

type ConsoleTableProps<T extends TableRowData> = {
    columns: TableProps<T>["columns"];
    data?: T[];
    error?: unknown;
    extra?: ReactNode;
    loading?: boolean;
    onRetry?: () => void;
    pagination?: PaginationProps | false;
    rowKey: string;
    tableProps?: Omit<TableProps<T>, "columns" | "data" | "loading" | "pagination" | "rowKey">;
    title?: ReactNode;
    toolbar?: ReactNode;
};

export function ConsoleTable<T extends TableRowData>({
    columns,
    data,
    error,
    extra,
    loading,
    onRetry,
    pagination,
    rowKey,
    tableProps,
    title,
    toolbar,
}: ConsoleTableProps<T>) {
    const prompt = useMessagePrompt();
    const canRetry = Boolean(onRetry);

    useEffect(() => {
        if (!error) {
            return;
        }

        const message = consoleErrorMessage(error);
        prompt.error({
            content: canRetry ? `${message}，请使用页面重试按钮。` : message,
        });
    }, [canRetry, error, prompt]);

    return (
        <Card
            actions={
                canRetry ? (
                    <div className="flex items-center justify-end gap-2">
                        {extra}
                        <Button onClick={onRetry} size="small" variant="outline">
                            重试
                        </Button>
                    </div>
                ) : (
                    extra
                )
            }
            className="console-table-card"
            header={title}
        >
            {toolbar ? <div className="mb-4">{toolbar}</div> : null}
            <Table<T>
                {...tableProps}
                columns={columns}
                data={data}
                disableDataPage
                empty={<Empty description="暂无数据" />}
                loading={loading}
                pagination={pagination === false ? undefined : pagination}
                rowKey={rowKey}
            />
        </Card>
    );
}
