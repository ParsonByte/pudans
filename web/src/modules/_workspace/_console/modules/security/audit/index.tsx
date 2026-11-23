"use client";

import { useEffect, useState } from "react";

import { type AuditRecord, apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { RefreshIcon } from "tdesign-icons-react";
import { Button, Select } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { GlobalDialog, ResultTag, SectionCard } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ActorRanking } from "../../../components/actor-ranking";
import { AuditTrendChart } from "../../../components/audit-trend-chart";
import { ConsoleTable } from "../../../components/console-table";
import { CopyableIdentifier } from "../../../components/copyable-identifier";
import { DurationBucketChart } from "../../../components/duration-bucket-chart";
import { ObjectDetails } from "../../../components/object-details";
import { OperationRanking } from "../../../components/operation-ranking";
import { ResultBreakdown } from "../../../components/result-breakdown";
import { WeekHourHeatmap } from "../../../components/week-hour-heatmap";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime, formatDuration, operationLabel } from "../../../composables/_format";
import { DASHBOARD_RANGES, type DashboardRange } from "../../../composables/_range";
import { RESULT_OPTIONS } from "../../../composables/_results";
import { useConsoleTableState } from "../../../composables/_table";
import { useDashboardTheme } from "../../overview/dashboard/composables/_theme";
import { SecuritySearchInput } from "../components/security-search-input";
import { AuditMetricGrid } from "./components/audit-metric-grid";

export function AuditView() {
    const table = useConsoleTableState();
    const prompt = useMessagePrompt();
    const { token } = useDashboardTheme();
    const [range, setRange] = useState<DashboardRange>(7);
    const [selected, setSelected] = useState<AuditRecord>();
    const uid = table.filters.uid ?? "";
    const operation = table.filters.operation ?? "";
    const result = table.filters.result ?? "";
    const overviewQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => (await apiClient.auditLog.getAuditOverview({ days: range })).data,
        queryKey: ["console", "audit", "overview", range],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const logsQuery = useQuery({
        placeholderData: keepPreviousData,
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
        queryKey: ["console", "audit", table.page, table.pageSize, uid, operation, result],
    });
    const columns: TableProps<AuditRecord>["columns"] = [
        {
            cell: ({ row }) => formatDateTime(row.timestamp),
            colKey: "timestamp",
            title: "时间",
            width: 168,
        },
        {
            cell: ({ row }) => <CopyableIdentifier value={row.uid} />,
            colKey: "uid",
            title: "用户",
            width: 210,
        },
        {
            cell: ({ row }) => <span title={row.operation}>{operationLabel(row.operation)}</span>,
            colKey: "operation",
            title: "操作",
            width: 120,
        },
        {
            cell: ({ row }) => row.resourceName ?? row.resourceType ?? "—",
            colKey: "resource",
            title: "资源",
            width: 150,
        },
        {
            cell: ({ row }) => <ResultTag result={row.result} />,
            colKey: "result",
            title: "结果",
            width: 92,
        },
        {
            cell: ({ row }) => formatDuration(row.durationMs),
            colKey: "durationMs",
            title: "耗时",
            width: 96,
        },
        {
            cell: ({ row }) => (
                <span className="text-xs" style={{ color: token.colorTextTertiary }}>
                    {row.requestMethod} {row.requestPath}
                </span>
            ),
            colKey: "request",
            title: "请求",
        },
        {
            cell: ({ row }) => (
                <Button onClick={() => setSelected(row)} size="small" variant="text">
                    详情
                </Button>
            ),
            colKey: "actions",
            title: "操作",
            width: 88,
        },
    ];
    const summary = overviewQuery.data?.summary;
    const isSyncing = overviewQuery.isFetching || logsQuery.isFetching;
    const refresh = () => {
        void Promise.all([overviewQuery.refetch(), logsQuery.refetch()]);
    };

    useEffect(() => {
        if (overviewQuery.error) {
            prompt.error({ content: consoleErrorMessage(overviewQuery.error) });
        }
    }, [overviewQuery.error, prompt]);

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex flex-wrap items-center justify-end gap-2">
                <Select
                    onChange={(value) => setRange(value as DashboardRange)}
                    options={DASHBOARD_RANGES}
                    style={{ width: 132 }}
                    value={range}
                />
                <Button icon={<RefreshIcon />} loading={isSyncing} onClick={refresh} variant="outline">
                    刷新
                </Button>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                <AuditMetricGrid loading={overviewQuery.isPending} summary={summary} />

                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="审计请求趋势">
                        <AuditTrendChart points={overviewQuery.data?.timeline} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="7×24 审计热力图">
                        <WeekHourHeatmap
                            cells={overviewQuery.data?.heatmap}
                            count={(cell) => cell.records}
                            countUnit="条"
                            emptyText="暂无热力图数据"
                            tone="blue"
                            valueLabel="审计记录"
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="操作分布">
                        <OperationRanking operations={overviewQuery.data?.operations} totalRecords={summary?.records} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="结果分布">
                        <ResultBreakdown results={overviewQuery.data?.results} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="耗时分布">
                        <DurationBucketChart
                            buckets={overviewQuery.data?.durations}
                            emptyText="暂无耗时分布"
                            title="请求耗时区间"
                            valueLabel="条记录"
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="活跃用户">
                        <ActorRanking actors={overviewQuery.data?.actors} />
                    </SectionCard>
                </div>
            </div>

            <ConsoleTable
                columns={columns}
                data={logsQuery.data?.items}
                error={logsQuery.error}
                extra={
                    <Button loading={logsQuery.isFetching} onClick={() => void logsQuery.refetch()} size="small">
                        刷新
                    </Button>
                }
                loading={logsQuery.isPending}
                onRetry={() => void logsQuery.refetch()}
                pagination={{ ...table.pagination, total: logsQuery.data?.total ?? 0 }}
                rowKey="metadata.name"
                title="审计日志"
                toolbar={
                    <div className="flex min-w-0 flex-wrap items-center gap-2">
                        <div className="min-w-0 flex-1 sm:max-w-[220px]">
                            <SecuritySearchInput
                                onChange={() => undefined}
                                onSearch={(value) => table.setFilter("uid", value || null)}
                                placeholder="用户 UID"
                                value={uid}
                            />
                        </div>
                        <div className="min-w-0 flex-1 sm:max-w-[220px]">
                            <SecuritySearchInput
                                onChange={() => undefined}
                                onSearch={(value) => table.setFilter("operation", value || null)}
                                placeholder="操作名称"
                                value={operation}
                            />
                        </div>
                        <Select
                            clearable
                            onChange={(value) => table.setFilter("result", (value as string | undefined) ?? null)}
                            options={RESULT_OPTIONS}
                            placeholder="结果"
                            style={{ width: 144 }}
                            value={result || undefined}
                        />
                        <Button onClick={() => table.reset()}>重置</Button>
                    </div>
                }
            />

            <GlobalDialog
                footer={false}
                onClose={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={720}
            >
                {selected ? <ObjectDetails value={selected} /> : null}
            </GlobalDialog>
        </div>
    );
}
