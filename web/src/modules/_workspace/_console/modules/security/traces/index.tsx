"use client";

import { useEffect, useMemo, useState } from "react";

import { type AuditSessionSummary, apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { HistoryIcon, RefreshIcon } from "tdesign-icons-react";
import { Breadcrumb, Button, Empty, RadioGroup, Select, Skeleton, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { GlobalDialog, ResultTag, SectionCard } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useQueryParams } from "@/hooks/utils/use-query-params";

import { ActorRanking } from "../../../components/actor-ranking";
import { AuditTrendChart } from "../../../components/audit-trend-chart";
import { ConsoleTable } from "../../../components/console-table";
import { CopyableIdentifier } from "../../../components/copyable-identifier";
import { DurationBucketChart } from "../../../components/duration-bucket-chart";
import { OperationRanking } from "../../../components/operation-ranking";
import { ResultBreakdown } from "../../../components/result-breakdown";
import { SessionMetricGrid } from "../../../components/session-metric-grid";
import { SessionPath } from "../../../components/session-path";
import { TraceWaterfall } from "../../../components/trace-waterfall";
import { WeekHourHeatmap } from "../../../components/week-hour-heatmap";
import { consoleErrorMessage } from "../../../composables/_errors";
import {
    formatCount,
    formatDateTime,
    formatDuration,
    maskIdentifier,
    operationLabel,
} from "../../../composables/_format";
import { DASHBOARD_RANGES, type DashboardRange } from "../../../composables/_range";
import { RESULT_OPTIONS } from "../../../composables/_results";
import { useConsoleTableState } from "../../../composables/_table";
import { dashboardRangeBounds, dashboardRangeForDays } from "../../overview/dashboard/composables/_dashboard";
import { useDashboardTheme } from "../../overview/dashboard/composables/_theme";
import { SecuritySearchInput } from "../components/security-search-input";

const OPERATION_LIMIT = 2;

const { BreadcrumbItem } = Breadcrumb;

type SessionRow = AuditSessionSummary & {
    rowId: string;
};

type TraceTab = "overview" | "sessions";

const TRACE_TAB_OPTIONS: Array<{ label: string; value: TraceTab }> = [
    { label: "链路概览", value: "overview" },
    { label: "用户会话", value: "sessions" },
];

export function TracesView() {
    const table = useConsoleTableState();
    const prompt = useMessagePrompt();
    const { setParams } = useQueryParams();
    const { token } = useDashboardTheme();
    const [range, setRange] = useState<DashboardRange>(7);
    const [activeTab, setActiveTab] = useState<TraceTab>("overview");
    const [selectedSession, setSelectedSession] = useState<AuditSessionSummary>();
    const uid = table.filters.uid ?? "";
    const operation = table.filters.operation ?? "";
    const result = table.filters.result ?? "";
    const rangeBounds = useMemo(() => dashboardRangeBounds(dashboardRangeForDays(range)), [range]);
    const overviewQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => (await apiClient.auditLog.getAuditOverview({ days: range })).data,
        queryKey: ["console", "traces", "overview", range],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const sessionsQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => {
            return (
                await apiClient.auditLog.listAuditSessions({
                    from: rangeBounds.from,
                    operation: operation || undefined,
                    page: table.page,
                    result: result || undefined,
                    size: table.pageSize,
                    to: rangeBounds.to,
                    uid: uid || undefined,
                })
            ).data;
        },
        queryKey: [
            "console",
            "traces",
            "sessions",
            table.page,
            table.pageSize,
            uid,
            operation,
            result,
            rangeBounds.from,
            rangeBounds.to,
        ],
    });
    const detailQuery = useQuery({
        enabled: Boolean(selectedSession?.sessionId),
        queryFn: async () =>
            (await apiClient.auditLog.replayAuditSession({ sessionId: selectedSession?.sessionId ?? "" })).data,
        queryKey: ["console", "traces", "session", selectedSession?.sessionId],
    });
    const sessionPage = sessionsQuery.data;
    const sessionRows = useMemo<SessionRow[]>(
        () =>
            (sessionPage?.items ?? []).map((session, index) => ({
                ...session,
                rowId: session.sessionId ?? `${session.uid ?? "session"}:${session.start ?? "unknown"}:${index}`,
            })),
        [sessionPage?.items],
    );
    const isSyncing = overviewQuery.isFetching || sessionsQuery.isFetching;
    const loadError = sessionsQuery.error ?? overviewQuery.error;
    const refresh = () => {
        void Promise.all([overviewQuery.refetch(), sessionsQuery.refetch()]);
    };
    const openReplay = (sessionId?: string) => {
        if (!sessionId) {
            return;
        }

        setParams({ flow: null, security: "replay", session: sessionId });
    };

    useEffect(() => {
        if (loadError) {
            prompt.error({ content: consoleErrorMessage(loadError) });
        }
    }, [loadError, prompt]);

    useEffect(() => {
        if (sessionPage?.truncated) {
            prompt.warning({ content: "会话列表达到扫描上限，请缩小时间范围后重新查看。" });
        }
    }, [prompt, sessionPage?.truncated]);

    const columns: TableProps<SessionRow>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="min-w-0">
                    <div className="truncate text-sm font-medium">{row.username ?? "未命名用户"}</div>
                    <CopyableIdentifier className="mt-0.5 text-[11px]" mask value={row.uid} />
                </div>
            ),
            colKey: "username",
            title: "用户",
            width: 190,
        },
        {
            cell: ({ row }) => (
                <SessionPath entryPath={row.entryPath} exitPath={row.exitPath} pageCount={row.pageCount} />
            ),
            colKey: "route",
            title: "会话路径",
            width: 360,
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-wrap gap-1">
                    <Tag theme="primary" variant="light">
                        {formatCount(row.pageCount)} 页
                    </Tag>
                    <Tag theme="success" variant="light">
                        {formatCount(row.actionCount)} 操作
                    </Tag>
                    <Tooltip content={`会话共 ${formatCount(row.recordCount)} 条审计记录`}>
                        <Tag>{formatCount(row.recordCount)} 记录</Tag>
                    </Tooltip>
                </div>
            ),
            colKey: "steps",
            title: "会话构成",
            width: 190,
        },
        {
            cell: ({ row }) => <ResultTag result={row.status} />,
            colKey: "status",
            title: "结果",
            width: 90,
        },
        {
            cell: ({ row }) => (
                <span className="text-xs whitespace-nowrap tabular-nums" style={{ color: token.colorTextSecondary }}>
                    {formatDateTime(row.start)}
                </span>
            ),
            colKey: "start",
            title: "开始时间",
            width: 168,
        },
        {
            cell: ({ row }) => <span className="whitespace-nowrap">{formatDuration(row.durationMs)}</span>,
            colKey: "durationMs",
            title: "耗时",
            width: 112,
        },
        {
            cell: ({ row }) => {
                const operations = row.operations ?? [];

                return (
                    <div className="flex flex-wrap gap-1">
                        {operations.slice(0, OPERATION_LIMIT).map((item) => (
                            <Tag key={item}>{operationLabel(item)}</Tag>
                        ))}
                        {operations.length > OPERATION_LIMIT ? (
                            <Tooltip content={operations.slice(OPERATION_LIMIT).map(operationLabel).join("、")}>
                                <Tag>+{operations.length - OPERATION_LIMIT}</Tag>
                            </Tooltip>
                        ) : null}
                        {operations.length === 0 ? "—" : null}
                    </div>
                );
            },
            colKey: "operations",
            title: "操作",
            width: 180,
        },
        {
            cell: ({ row }) =>
                row.archived ? (
                    <Tag theme="warning" variant="light">
                        含归档
                    </Tag>
                ) : (
                    <Tag>活跃</Tag>
                ),
            colKey: "archived",
            title: "来源",
            width: 90,
        },
        {
            cell: ({ row }) => (
                <div className="flex items-center gap-1">
                    <Button onClick={() => setSelectedSession(row)} size="small">
                        展开
                    </Button>
                    <Button
                        disabled={!row.sessionId}
                        icon={<HistoryIcon />}
                        onClick={() => openReplay(row.sessionId)}
                        size="small"
                        theme="primary"
                        variant="text"
                    >
                        回溯
                    </Button>
                </div>
            ),
            colKey: "actions",
            fixed: "right",
            title: "操作",
            width: 132,
        },
    ];

    return (
        <div className="flex w-full min-w-0 flex-col gap-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <Breadcrumb>
                    <BreadcrumbItem>安全与审计</BreadcrumbItem>
                    <BreadcrumbItem>链路</BreadcrumbItem>
                </Breadcrumb>
                <div className="flex flex-wrap items-center gap-2">
                    <Select
                        aria-label="链路统计范围"
                        onChange={(value) => {
                            setRange(value as DashboardRange);
                            table.setPage(1);
                        }}
                        options={DASHBOARD_RANGES}
                        style={{ width: 132 }}
                        value={range}
                    />
                    <Button icon={<RefreshIcon />} loading={isSyncing} onClick={refresh} variant="outline">
                        刷新
                    </Button>
                    <Button icon={<HistoryIcon />} onClick={() => setParams({ security: "replay" })}>
                        回溯与归档
                    </Button>
                </div>
            </div>

            <RadioGroup
                className="w-fit"
                onChange={(value) => setActiveTab(value as TraceTab)}
                options={TRACE_TAB_OPTIONS}
                theme="button"
                value={activeTab}
                variant="default-filled"
            />

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12" hidden={activeTab !== "overview"}>
                <SessionMetricGrid loading={sessionsQuery.isPending} page={sessionPage} />

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
                            emptyText="暂无审计热力图数据"
                            tone="blue"
                            valueLabel="审计记录"
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="操作分布">
                        <OperationRanking
                            operations={overviewQuery.data?.operations}
                            totalRecords={overviewQuery.data?.summary?.records}
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="结果分布">
                        <ResultBreakdown results={overviewQuery.data?.results} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-6">
                    <SectionCard title="请求耗时分布">
                        <DurationBucketChart
                            buckets={overviewQuery.data?.durations}
                            emptyText="暂无耗时分布"
                            title="请求耗时区间"
                            valueLabel="条记录"
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-6">
                    <SectionCard title="活跃用户">
                        <ActorRanking actors={overviewQuery.data?.actors} />
                    </SectionCard>
                </div>
            </div>

            <div hidden={activeTab !== "sessions"}>
                <SectionCard title="用户会话链路">
                    <ConsoleTable
                        cardless
                        columns={columns}
                        data={sessionRows}
                        error={sessionsQuery.error}
                        loading={sessionsQuery.isPending}
                        onRetry={sessionsQuery.error ? () => void sessionsQuery.refetch() : undefined}
                        pagination={{ ...table.pagination, total: sessionPage?.total ?? 0 }}
                        rowKey="rowId"
                        toolbar={
                            <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
                                <div className="min-w-0 sm:max-w-[220px] sm:flex-1">
                                    <SecuritySearchInput
                                        onChange={() => undefined}
                                        onSearch={(value) => table.setFilter("uid", value || null)}
                                        placeholder="用户 UID"
                                        value={uid}
                                    />
                                </div>
                                <div className="min-w-0 sm:max-w-[220px] sm:flex-1">
                                    <SecuritySearchInput
                                        onChange={() => undefined}
                                        onSearch={(value) => table.setFilter("operation", value || null)}
                                        placeholder="会话内的操作"
                                        value={operation}
                                    />
                                </div>
                                <div className="flex items-center gap-2">
                                    <Select
                                        clearable
                                        onChange={(value) =>
                                            table.setFilter("result", (value as string | undefined) ?? null)
                                        }
                                        options={RESULT_OPTIONS}
                                        placeholder="会话结果"
                                        style={{ width: 144 }}
                                        value={result || undefined}
                                    />
                                    <Button onClick={() => table.reset()}>重置</Button>
                                </div>
                            </div>
                        }
                    />
                </SectionCard>
            </div>

            <GlobalDialog
                footer={false}
                onClose={() => setSelectedSession(undefined)}
                open={Boolean(selectedSession)}
                style={{ height: "calc(100dvh - 96px)" }}
                title={`用户会话 · ${selectedSession?.username ?? maskIdentifier(selectedSession?.uid)}`}
                width={1120}
            >
                {detailQuery.isPending ? (
                    <Skeleton theme="paragraph" />
                ) : detailQuery.isError ? (
                    <Empty
                        action={
                            <Button loading={detailQuery.isFetching} onClick={() => void detailQuery.refetch()}>
                                重试
                            </Button>
                        }
                        description={consoleErrorMessage(detailQuery.error)}
                        type="network-error"
                    />
                ) : (
                    <div className="flex min-h-0 flex-1 flex-col pr-3">
                        <TraceWaterfall
                            key={detailQuery.data?.session?.sessionId}
                            layout="dialog"
                            session={detailQuery.data?.session ?? selectedSession}
                            steps={detailQuery.data?.steps}
                        />
                    </div>
                )}
            </GlobalDialog>
        </div>
    );
}
