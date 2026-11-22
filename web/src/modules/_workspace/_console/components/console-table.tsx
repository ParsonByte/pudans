"use client";

import { type ReactNode, useEffect } from "react";

import { Card, Empty, Table, type TablePaginationConfig, type TableProps } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useEventCallback } from "@/hooks/utils/use-event-callback";

import { consoleErrorMessage } from "../composables/_errors";

type ConsoleTableProps<T extends object> = {
    columns: TableProps<T>["columns"];
    data?: T[];
    error?: unknown;
    extra?: ReactNode;
    loading?: boolean;
    onRetry?: () => void;
    pagination?: TablePaginationConfig | false;
    rowKey: TableProps<T>["rowKey"];
    title?: ReactNode;
    toolbar?: ReactNode;
};

export function ConsoleTable<T extends object>({
    columns,
    data,
    error,
    extra,
    loading,
    onRetry,
    pagination,
    rowKey,
    title,
    toolbar,
}: ConsoleTableProps<T>) {
    const prompt = useMessagePrompt();
    const isMobile = useMobile();
    const retry = useEventCallback(() => onRetry?.());
    const canRetry = Boolean(onRetry);

    useEffect(() => {
        if (!error) {
            return;
        }

        const message = consoleErrorMessage(error);
        prompt.error({
            content: canRetry ? `${message}，点击重试。` : message,
            key: "console-table-error",
            onClick: canRetry ? retry : undefined,
        });
    }, [canRetry, error, prompt, retry]);

    return (
        <Card className="console-table-card" extra={extra} title={title}>
            {toolbar ? <div className="mb-4">{toolbar}</div> : null}
            <Table<T>
                columns={columns}
                dataSource={data}
                loading={loading}
                locale={{ emptyText: <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
                pagination={pagination ?? false}
                rowKey={rowKey}
                scroll={{ x: "max-content" }}
                size={isMobile ? "small" : "middle"}
            />
        </Card>
    );
}
