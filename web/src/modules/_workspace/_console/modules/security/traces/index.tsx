"use client";

import { useState } from "react";

import { type AuditTraceSummary, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Modal, Select, Space, type TableProps } from "antd";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { ResultTag } from "../../../components/result-tag";
import { formatDateTime, formatDuration } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

const RESULT_OPTIONS = [
    { label: "成功", value: "SUCCESS" },
    { label: "失败", value: "FAILURE" },
];

export function TracesView() {
    const table = useConsoleTableState();
    const [selected, setSelected] = useState<AuditTraceSummary>();
    const uid = table.filters.uid ?? "";
    const operation = table.filters.operation ?? "";
    const result = table.filters.result ?? "";
    const tracesQuery = useQuery({
        queryKey: ["console", "traces", table.page, table.pageSize, uid, operation, result],
        queryFn: async () =>
            (
                await apiClient.auditLog.listAuditTraceFlows({
                    operation: operation || undefined,
                    page: table.page,
                    result: result || undefined,
                    size: table.pageSize,
                    uid: uid || undefined,
                })
            ).data,
    });

    const columns: TableProps<AuditTraceSummary>["columns"] = [
        { dataIndex: "traceFlowId", key: "traceFlowId", title: "链路 ID" },
        { dataIndex: "uid", key: "uid", title: "用户" },
        {
            dataIndex: "status",
            key: "status",
            render: (value: string) => <ResultTag result={value} />,
            title: "状态",
        },
        {
            dataIndex: "spanCount",
            key: "spanCount",
            render: (value: number) => value ?? 0,
            title: "步骤数",
        },
        {
            dataIndex: "durationMs",
            key: "durationMs",
            render: (value: number) => formatDuration(value),
            title: "耗时",
        },
        {
            dataIndex: "start",
            key: "start",
            render: (value: string) => formatDateTime(value),
            title: "开始时间",
        },
        {
            dataIndex: "end",
            key: "end",
            render: (value: string) => formatDateTime(value),
            title: "结束时间",
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
                data={tracesQuery.data?.items}
                error={tracesQuery.error}
                extra={
                    <Button onClick={() => void tracesQuery.refetch()} size="small">
                        刷新
                    </Button>
                }
                loading={tracesQuery.isPending}
                onRetry={() => void tracesQuery.refetch()}
                pagination={{ ...table.pagination, total: tracesQuery.data?.total ?? 0 }}
                rowKey={(record) => record.traceFlowId ?? ""}
                title="链路"
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
                            placeholder="状态"
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
                title={selected?.traceFlowId}
                width={720}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </>
    );
}
