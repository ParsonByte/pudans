"use client";

import { useState } from "react";

import { type AuditRecord, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Modal, Select, Space, type TableProps } from "antd";

import { ResultTag } from "@/components/ui";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { formatDateTime, formatDuration } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

const RESULT_OPTIONS = [
    { label: "成功", value: "SUCCESS" },
    { label: "失败", value: "FAILURE" },
];

export function AuditView() {
    const table = useConsoleTableState();
    const [selected, setSelected] = useState<AuditRecord>();
    const uid = table.filters.uid ?? "";
    const operation = table.filters.operation ?? "";
    const result = table.filters.result ?? "";
    const logsQuery = useQuery({
        queryKey: ["console", "audit", table.page, table.pageSize, uid, operation, result],
        queryFn: async () =>
            (
                await apiClient.auditLog.listAuditLogs({
                    operation: operation || undefined,
                    page: table.page,
                    result: result || undefined,
                    size: table.pageSize,
                    uid: uid || undefined,
                })
            ).data,
    });

    const columns: TableProps<AuditRecord>["columns"] = [
        {
            dataIndex: "timestamp",
            key: "timestamp",
            render: (value: string) => formatDateTime(value),
            title: "时间",
        },
        { dataIndex: "uid", key: "uid", title: "用户" },
        { dataIndex: "operation", key: "operation", title: "操作" },
        {
            key: "resource",
            render: (_, record) => record.resourceName ?? record.resourceType ?? "—",
            title: "资源",
        },
        {
            dataIndex: "result",
            key: "result",
            render: (value: string) => <ResultTag result={value} />,
            title: "结果",
        },
        {
            dataIndex: "durationMs",
            key: "durationMs",
            render: (value: number) => formatDuration(value),
            title: "耗时",
        },
        {
            key: "request",
            render: (_, record) => (
                <span className="text-xs text-neutral-500">
                    {record.requestMethod} {record.requestPath}
                </span>
            ),
            title: "请求",
        },
        {
            key: "actions",
            render: (_, record) => (
                <Button onClick={() => setSelected(record)} size="small">
                    详情
                </Button>
            ),
            title: "操作",
        },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={logsQuery.data?.items}
                error={logsQuery.error}
                extra={
                    <Button onClick={() => void logsQuery.refetch()} size="small">
                        刷新
                    </Button>
                }
                loading={logsQuery.isPending}
                onRetry={() => void logsQuery.refetch()}
                pagination={{ ...table.pagination, total: logsQuery.data?.total ?? 0 }}
                rowKey={(record) => record.metadata.name}
                title="审计日志"
                toolbar={
                    <Space className="w-full sm:w-auto" wrap>
                        <Input.Search
                            allowClear
                            className="w-full sm:w-[200px]"
                            defaultValue={uid}
                            key={`uid:${uid}`}
                            onSearch={(value) => table.setFilter("uid", value.trim() || null)}
                            placeholder="用户 UID"
                        />
                        <Input.Search
                            allowClear
                            className="w-full sm:w-[200px]"
                            defaultValue={operation}
                            key={`operation:${operation}`}
                            onSearch={(value) => table.setFilter("operation", value.trim() || null)}
                            placeholder="操作名称"
                        />
                        <Select
                            allowClear
                            className="w-full sm:w-36"
                            onChange={(value) => table.setFilter("result", value ?? null)}
                            options={RESULT_OPTIONS}
                            placeholder="结果"
                            value={result || undefined}
                        />
                        <Button className="w-full sm:w-auto" onClick={() => table.reset()}>
                            重置
                        </Button>
                    </Space>
                }
            />
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={720}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </>
    );
}
