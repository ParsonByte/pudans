"use client";

import { useEffect, useState } from "react";

import { type AuditArchiveStats, type AuditRecord, type AuditReplay, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Col, Empty, Flex, Input, Modal, Row, Skeleton, type TableProps, Timeline } from "antd";

import { MetricCard, ResultTag, SectionCard } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime, formatDuration } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

export function ReplayView() {
    const prompt = useMessagePrompt();
    const table = useConsoleTableState();
    const [traceFlowId, setTraceFlowId] = useState("");
    const [selected, setSelected] = useState<AuditRecord>();
    const statsQuery = useQuery({
        queryKey: ["console", "replay", "stats"],
        queryFn: async () => (await apiClient.auditLog.getAuditArchiveStats()).data,
    });
    const archivedQuery = useQuery({
        queryKey: ["console", "replay", "archived", table.page, table.pageSize],
        queryFn: async () =>
            (await apiClient.auditLog.listArchivedAuditLogs({ page: table.page, size: table.pageSize })).data,
    });
    const replayQuery = useQuery({
        enabled: Boolean(traceFlowId),
        queryKey: ["console", "replay", "flow", traceFlowId],
        queryFn: async () => (await apiClient.auditLog.replayAuditFlow({ traceFlowId })).data,
    });
    const stats: AuditArchiveStats | undefined = statsQuery.data;
    const replay: AuditReplay | undefined = replayQuery.data;

    useEffect(() => {
        if (!statsQuery.isError) {
            return;
        }

        prompt.error({
            content: consoleErrorMessage(statsQuery.error),
            key: "replay-stats-error",
        });
    }, [prompt, statsQuery.error, statsQuery.isError]);

    useEffect(() => {
        if (!replayQuery.isError) {
            return;
        }

        prompt.error({
            content: consoleErrorMessage(replayQuery.error),
            key: "replay-flow-error",
        });
    }, [prompt, replayQuery.error, replayQuery.isError]);

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
        <Flex vertical gap={16}>
            <Row gutter={[16, 16]}>
                <Col lg={8} xs={24}>
                    <MetricCard label="活跃审计记录" loading={statsQuery.isPending} value={stats?.activeCount ?? 0} />
                </Col>
                <Col lg={8} xs={24}>
                    <MetricCard label="已归档记录" loading={statsQuery.isPending} value={stats?.archivedCount ?? 0} />
                </Col>
                <Col lg={8} xs={24}>
                    <MetricCard label="记录总数" loading={statsQuery.isPending} value={stats?.totalCount ?? 0} />
                </Col>
            </Row>

            <SectionCard title="回放链路">
                <Flex vertical gap={16}>
                    <Input.Search
                        allowClear
                        enterButton="回放"
                        loading={replayQuery.isFetching}
                        onSearch={(value) => setTraceFlowId(value.trim())}
                        placeholder="输入 traceFlowId"
                    />
                    {!traceFlowId ? (
                        <Empty description="输入链路 ID 后开始回放" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : replayQuery.isPending ? (
                        <Skeleton active />
                    ) : replayQuery.isError ? (
                        <Empty description="链路回放失败" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (replay?.steps ?? []).length === 0 ? (
                        <Empty description="该链路没有可回放的步骤" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                        <Timeline
                            items={(replay?.steps ?? []).map((step) => ({
                                children: (
                                    <div className="space-y-1">
                                        <div className="font-medium">{step.operation ?? "—"}</div>
                                        <div className="text-xs text-neutral-500">
                                            {step.requestMethod} {step.requestPath}
                                        </div>
                                        <div className="text-xs text-neutral-500">
                                            {formatDateTime(step.timestamp)} · {formatDuration(step.durationMs)}
                                        </div>
                                        <ResultTag result={step.result} />
                                    </div>
                                ),
                                color: step.result === "FAILURE" ? "red" : "green",
                            }))}
                        />
                    )}
                </Flex>
            </SectionCard>

            <ConsoleTable
                columns={columns}
                data={archivedQuery.data?.items}
                error={archivedQuery.error}
                extra={
                    <Button onClick={() => void archivedQuery.refetch()} size="small">
                        刷新
                    </Button>
                }
                loading={archivedQuery.isPending}
                onRetry={() => void archivedQuery.refetch()}
                pagination={{ ...table.pagination, total: archivedQuery.data?.total ?? 0 }}
                rowKey={(record) => record.metadata.name}
                title="归档审计"
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
        </Flex>
    );
}
