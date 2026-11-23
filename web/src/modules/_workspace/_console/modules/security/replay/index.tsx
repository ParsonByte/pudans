"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { type AuditRecord, apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ArrowLeftIcon, ArrowRightIcon, FileZipIcon, PlayIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Empty, Input, Select, Skeleton } from "tdesign-react";
import type { InputRef, TableProps } from "tdesign-react";

import { GlobalDialog, ResultTag, SectionCard } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";
import { useQueryParams } from "@/hooks/utils/use-query-params";

import { ActorRanking } from "../../../components/actor-ranking";
import { AuditTrendChart } from "../../../components/audit-trend-chart";
import { ConsoleTable } from "../../../components/console-table";
import { CopyableIdentifier } from "../../../components/copyable-identifier";
import { DurationBucketChart } from "../../../components/duration-bucket-chart";
import { ObjectDetails } from "../../../components/object-details";
import { OperationRanking } from "../../../components/operation-ranking";
import { ResultBreakdown } from "../../../components/result-breakdown";
import { SessionMetricGrid } from "../../../components/session-metric-grid";
import { TraceWaterfall } from "../../../components/trace-waterfall";
import { WeekHourHeatmap } from "../../../components/week-hour-heatmap";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime, formatDuration, operationLabel } from "../../../composables/_format";
import { DASHBOARD_RANGES, type DashboardRange } from "../../../composables/_range";
import { RESULT_OPTIONS } from "../../../composables/_results";
import { useConsoleSession } from "../../../composables/_session";
import { useConsoleTableState } from "../../../composables/_table";
import { useDashboardTheme } from "../../overview/dashboard/composables/_theme";
import { SecuritySearchInput } from "../components/security-search-input";
import { ArchiveAuditModal } from "./components/archive-audit-modal";

const RECENT_SESSION_LIMIT = 50;
const LEGACY_FLOW_WINDOW_MS = 30 * 60 * 1000;

const REPLAY_PERMISSIONS = {
    archive: "replay:archive",
    view: "replay:view",
} as const;

function isSessionId(value: string): boolean {
    return /^[^:]+:\d+(?::\d+)?$/.test(value);
}

function timestampOf(value?: string): number {
    if (!value) {
        return 0;
    }

    const timestamp = Date.parse(value);

    return Number.isNaN(timestamp) ? 0 : timestamp;
}

function stepStart(value?: string, durationMs?: number): number {
    return timestampOf(value) - Math.max(0, durationMs ?? 0);
}

export function ReplayView() {
    const table = useConsoleTableState();
    const prompt = useMessagePrompt();
    const { searchParams, setParams } = useQueryParams();
    const sessionQuery = useConsoleSession();
    const { can } = usePermission({ permissions: sessionQuery.data?.uiPermissions }, REPLAY_PERMISSIONS.view);
    const canArchive = can(REPLAY_PERMISSIONS.archive);
    const { token } = useDashboardTheme();
    const [range, setRange] = useState<DashboardRange>(7);
    const [selected, setSelected] = useState<AuditRecord>();
    const [archiveOpen, setArchiveOpen] = useState(false);
    const requestedSessionId = searchParams.get("session") ?? undefined;
    const requestedFlowId = searchParams.get("flow") ?? undefined;
    const uid = table.filters.uid ?? "";
    const operation = table.filters.operation ?? "";
    const result = table.filters.result ?? "";
    const overviewQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => (await apiClient.auditLog.getAuditOverview({ days: range })).data,
        queryKey: ["console", "replay", "overview", range],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const recentSessionsQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => {
            const from = new Date(Date.now() - range * 86_400_000).toISOString();

            return (
                await apiClient.auditLog.listAuditSessions({
                    from,
                    page: 1,
                    size: RECENT_SESSION_LIMIT,
                })
            ).data;
        },
        queryKey: ["console", "replay", "sessions", range],
    });
    const recentSessions = useMemo(() => recentSessionsQuery.data?.items ?? [], [recentSessionsQuery.data?.items]);
    const legacyFlowQuery = useQuery({
        enabled: Boolean(requestedFlowId && !requestedSessionId),
        queryFn: async () => (await apiClient.auditLog.replayAuditFlow({ traceFlowId: requestedFlowId ?? "" })).data,
        queryKey: ["console", "replay", "legacy-flow", requestedFlowId],
    });
    const legacySteps = useMemo(() => legacyFlowQuery.data?.steps ?? [], [legacyFlowQuery.data?.steps]);
    const legacyWindow = useMemo(() => {
        if (legacySteps.length === 0) {
            return undefined;
        }

        return {
            end: Math.max(...legacySteps.map((step) => timestampOf(step.timestamp))),
            start: Math.min(...legacySteps.map((step) => stepStart(step.timestamp, step.durationMs))),
        };
    }, [legacySteps]);
    const legacyUid = legacySteps[0]?.uid;
    const legacyFrom = legacyWindow ? new Date(legacyWindow.start - LEGACY_FLOW_WINDOW_MS).toISOString() : undefined;
    const legacyTo = legacyWindow ? new Date(legacyWindow.end + LEGACY_FLOW_WINDOW_MS).toISOString() : undefined;
    const legacySessionsQuery = useQuery({
        enabled: Boolean(requestedFlowId && !requestedSessionId && legacyUid && legacyFrom && legacyTo && legacyWindow),
        queryFn: async () =>
            (
                await apiClient.auditLog.listAuditSessions({
                    from: legacyFrom,
                    page: 1,
                    size: 100,
                    to: legacyTo,
                    uid: legacyUid,
                })
            ).data,
        queryKey: ["console", "replay", "legacy-session", legacyUid, legacyFrom, legacyTo],
    });
    const activeSessionId = requestedSessionId ?? (!requestedFlowId ? recentSessions[0]?.sessionId : undefined) ?? "";
    const replayQuery = useQuery({
        enabled: Boolean(activeSessionId),
        placeholderData: keepPreviousData,
        queryFn: async () => (await apiClient.auditLog.replayAuditSession({ sessionId: activeSessionId })).data,
        queryKey: ["console", "replay", "session", activeSessionId],
    });
    const archivedQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () =>
            (
                await apiClient.auditLog.listArchivedAuditLogs({
                    operation: operation || undefined,
                    page: table.page,
                    result: result || undefined,
                    size: table.pageSize,
                    uid: uid || undefined,
                })
            ).data,
        queryKey: ["console", "replay", "archived", table.page, table.pageSize, uid, operation, result],
    });
    const replayInputRef = useRef<InputRef>(null);
    const overview = overviewQuery.data;
    const summary = overview?.summary;
    const sessionPage = recentSessionsQuery.data;
    const activeSession =
        replayQuery.data?.session ?? recentSessions.find((session) => session.sessionId === activeSessionId);
    const activeSessionIndex = recentSessions.findIndex((session) => session.sessionId === activeSessionId);
    const previousSession = activeSessionIndex > 0 ? recentSessions[activeSessionIndex - 1] : undefined;
    const nextSession =
        activeSessionIndex >= 0 && activeSessionIndex < recentSessions.length - 1
            ? recentSessions[activeSessionIndex + 1]
            : undefined;
    const isSyncing =
        overviewQuery.isFetching ||
        recentSessionsQuery.isFetching ||
        replayQuery.isFetching ||
        archivedQuery.isFetching;
    const loadError = overviewQuery.error ?? recentSessionsQuery.error;
    const sessionOptions = recentSessions
        .filter((session) => Boolean(session.sessionId))
        .map((session) => ({
            label: `${session.username ?? session.uid ?? "未知用户"} · ${formatDateTime(session.start)} · ${session.entryPath ?? "—"} → ${session.exitPath ?? "—"}`,
            value: session.sessionId as string,
        }));
    const requestedLegacyResolution = Boolean(requestedFlowId && !requestedSessionId);
    const legacyPending =
        requestedLegacyResolution &&
        (legacyFlowQuery.isPending || Boolean(legacyWindow && legacySessionsQuery.isFetching));
    const legacyUnresolved = requestedLegacyResolution && !legacyPending && !legacyFlowQuery.isError;

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

    useEffect(() => {
        if (replayQuery.data?.truncated) {
            prompt.warning({ content: "当前会话步骤达到扫描上限，仅回放上限内的记录。" });
        }
    }, [prompt, replayQuery.data?.truncated]);

    useEffect(() => {
        if (legacyFlowQuery.error) {
            prompt.error({ content: consoleErrorMessage(legacyFlowQuery.error) });
        }
    }, [legacyFlowQuery.error, prompt]);

    useEffect(() => {
        if (replayQuery.error) {
            prompt.error({ content: consoleErrorMessage(replayQuery.error) });
        }
    }, [prompt, replayQuery.error]);

    useEffect(() => {
        if (!requestedSessionId || !activeSession?.uid || !activeSession.start || !activeSession.end) {
            return;
        }
        if (recentSessions.some((session) => session.sessionId === requestedSessionId)) {
            return;
        }

        const activeStart = timestampOf(activeSession.start);
        const activeEnd = timestampOf(activeSession.end);
        const covering = recentSessions.find((session) => {
            if (session.uid !== activeSession.uid || !session.sessionId) {
                return false;
            }

            return timestampOf(session.start) <= activeStart && timestampOf(session.end) >= activeEnd;
        });
        if (covering?.sessionId) {
            setParams({ session: covering.sessionId });
        }
    }, [activeSession, recentSessions, requestedSessionId, setParams]);

    useEffect(() => {
        if (!requestedFlowId || requestedSessionId || !legacyUid || !legacyWindow || !legacySessionsQuery.data) {
            return;
        }

        const match = legacySessionsQuery.data.items?.find((session) => {
            const start = timestampOf(session.start);
            const end = timestampOf(session.end);

            return session.uid === legacyUid && start <= legacyWindow.start && end >= legacyWindow.end;
        });
        if (match?.sessionId) {
            setParams({ flow: null, session: match.sessionId });
        }
    }, [legacySessionsQuery.data, legacyUid, legacyWindow, requestedFlowId, requestedSessionId, setParams]);

    const refresh = () => {
        void Promise.all([
            overviewQuery.refetch(),
            recentSessionsQuery.refetch(),
            activeSessionId ? replayQuery.refetch() : undefined,
            archivedQuery.refetch(),
        ]);
    };
    const scrollToArchive = () => {
        document.getElementById("archive-audit")?.scrollIntoView({ behavior: "smooth", block: "start" });
    };
    const selectSession = (sessionId?: string) => {
        const nextSessionId = sessionId?.trim();
        if (!nextSessionId) {
            return;
        }

        setParams({ flow: null, session: nextSessionId });
        window.setTimeout(() => {
            document.getElementById("replay-workbench")?.scrollIntoView({ behavior: "smooth", block: "start" });
        }, 0);
    };
    const startReplay = (value: string) => {
        const nextValue = value.trim();
        if (!nextValue) {
            return;
        }

        if (isSessionId(nextValue)) {
            setParams({ flow: null, session: nextValue });
        } else {
            setParams({ flow: nextValue, session: null });
        }
        window.setTimeout(() => {
            document.getElementById("replay-workbench")?.scrollIntoView({ behavior: "smooth", block: "start" });
        }, 0);
    };

    const columns: TableProps<AuditRecord>["columns"] = [
        {
            cell: ({ row }) => (
                <span className="text-xs whitespace-nowrap tabular-nums" style={{ color: token.colorTextSecondary }}>
                    {formatDateTime(row.timestamp)}
                </span>
            ),
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
            cell: ({ row }) => <CopyableIdentifier value={row.traceFlowId} />,
            colKey: "traceFlowId",
            title: "请求链路",
            width: 210,
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
                <Button icon={<FileZipIcon />} onClick={scrollToArchive} variant="outline">
                    归档记录
                </Button>
                {canArchive ? (
                    <Button icon={<FileZipIcon />} onClick={() => setArchiveOpen(true)} theme="primary">
                        发起归档
                    </Button>
                ) : null}
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                <SessionMetricGrid loading={recentSessionsQuery.isPending} page={sessionPage} />

                <div className="md:col-span-2 xl:col-span-12" id="replay-workbench">
                    <SectionCard title="整段会话回放">
                        <div className="flex min-w-0 flex-col gap-4">
                            <div className="flex min-w-0 flex-col gap-2 xl:flex-row xl:items-center">
                                <div className="flex min-w-0 flex-1 gap-2">
                                    <Input
                                        clearable
                                        defaultValue={activeSessionId}
                                        key={activeSessionId}
                                        onEnter={(value) => startReplay(String(value ?? ""))}
                                        placeholder="输入会话 ID；旧 traceFlowId 会自动定位所属会话"
                                        ref={replayInputRef}
                                    />
                                    <Button
                                        icon={<PlayIcon />}
                                        loading={legacyFlowQuery.isFetching || replayQuery.isFetching}
                                        onClick={() => startReplay(replayInputRef.current?.inputElement.value ?? "")}
                                    >
                                        回溯
                                    </Button>
                                </div>
                                <div className="flex flex-wrap items-center gap-2">
                                    <Select
                                        clearable
                                        filterable
                                        onChange={(value) => {
                                            if (value !== undefined) {
                                                selectSession(String(value));
                                            }
                                        }}
                                        options={sessionOptions}
                                        placeholder="选择最近会话"
                                        style={{ width: 320 }}
                                        value={activeSessionIndex >= 0 ? activeSessionId : undefined}
                                    />
                                    <Button
                                        disabled={!previousSession}
                                        icon={<ArrowLeftIcon />}
                                        onClick={() => selectSession(previousSession?.sessionId)}
                                        size="small"
                                    >
                                        上一会话
                                    </Button>
                                    <Button
                                        disabled={!nextSession}
                                        icon={<ArrowRightIcon />}
                                        onClick={() => selectSession(nextSession?.sessionId)}
                                        size="small"
                                    >
                                        下一会话
                                    </Button>
                                </div>
                            </div>

                            {legacyPending ? (
                                <Skeleton theme="paragraph" />
                            ) : legacyFlowQuery.isError ? (
                                <Empty
                                    action={<Button onClick={() => void legacyFlowQuery.refetch()}>重试</Button>}
                                    description={consoleErrorMessage(legacyFlowQuery.error)}
                                    type="network-error"
                                />
                            ) : legacyUnresolved ? (
                                <Empty description="旧链路未关联到完整会话" size="small" />
                            ) : !activeSessionId ? (
                                <Empty description="尚未选择会话" size="small" />
                            ) : replayQuery.isPending && !replayQuery.data ? (
                                <Skeleton theme="paragraph" />
                            ) : replayQuery.isError ? (
                                <Empty
                                    action={
                                        <Button
                                            loading={replayQuery.isFetching}
                                            onClick={() => void replayQuery.refetch()}
                                        >
                                            重试
                                        </Button>
                                    }
                                    description={consoleErrorMessage(replayQuery.error)}
                                    type="network-error"
                                />
                            ) : (
                                <TraceWaterfall
                                    key={replayQuery.data?.session?.sessionId ?? activeSessionId}
                                    session={activeSession}
                                    steps={replayQuery.data?.steps}
                                />
                            )}
                        </div>
                    </SectionCard>
                </div>
            </div>

            <div className="scroll-mt-24" id="archive-audit">
                <ConsoleTable
                    columns={columns}
                    data={archivedQuery.data?.items}
                    error={archivedQuery.error}
                    extra={
                        <Button
                            loading={archivedQuery.isFetching}
                            onClick={() => void archivedQuery.refetch()}
                            size="small"
                        >
                            刷新
                        </Button>
                    }
                    loading={archivedQuery.isPending}
                    onRetry={() => void archivedQuery.refetch()}
                    pagination={{ ...table.pagination, total: archivedQuery.data?.total ?? 0 }}
                    rowKey="metadata.name"
                    title="归档审计"
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
                            {canArchive ? (
                                <Button icon={<FileZipIcon />} onClick={() => setArchiveOpen(true)} theme="primary">
                                    发起归档
                                </Button>
                            ) : null}
                        </div>
                    }
                />
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="审计请求趋势">
                        <AuditTrendChart points={overview?.timeline} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="7×24 审计热力图">
                        <WeekHourHeatmap
                            cells={overview?.heatmap}
                            count={(cell) => cell.records}
                            countUnit="条"
                            emptyText="暂无审计热力图数据"
                            tone="purple"
                            valueLabel="审计记录"
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="操作分布">
                        <OperationRanking operations={overview?.operations} totalRecords={summary?.records} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="结果分布">
                        <ResultBreakdown results={overview?.results} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-6">
                    <SectionCard title="请求耗时分布">
                        <DurationBucketChart
                            buckets={overview?.durations}
                            emptyText="暂无耗时分布"
                            title="请求耗时区间"
                            valueLabel="条记录"
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-6">
                    <SectionCard title="活跃用户">
                        <ActorRanking actors={overview?.actors} />
                    </SectionCard>
                </div>
            </div>

            <GlobalDialog
                footer={false}
                onClose={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={720}
            >
                {selected ? <ObjectDetails value={selected} /> : null}
            </GlobalDialog>

            {archiveOpen ? <ArchiveAuditModal onClose={() => setArchiveOpen(false)} /> : null}
        </div>
    );
}
