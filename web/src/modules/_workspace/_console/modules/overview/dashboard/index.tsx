"use client";

import { type ComponentType, useEffect, useMemo, useRef, useState } from "react";

import { type AuditJourney, type AuditJourneyPage, type RecentOperation, apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import {
    CheckCircleIcon,
    ErrorCircleIcon,
    ErrorTriangleIcon,
    InfoCircleIcon,
    RefreshIcon,
    ShieldErrorIcon,
} from "tdesign-icons-react";
import type { IconProps } from "tdesign-icons-react";
import {
    Button,
    Card,
    Col,
    Empty,
    Link,
    type PrimaryTableCol,
    Progress,
    Row,
    Select,
    Skeleton,
    Table,
    Tooltip,
} from "tdesign-react";

import { ResultTag, SectionCard, StatTile } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { ActivityTrendChart } from "./components/activity-trend-chart";
import { MetricCard } from "./components/metric-card";
import { RecentUserJourneys } from "./components/recent-user-journeys";
import { RoleDistributionChart } from "./components/role-distribution-chart";
import {
    DASHBOARD_METRICS,
    DASHBOARD_METRIC_ORDER,
    DASHBOARD_RANGES,
    type DashboardData,
    type DashboardInsight,
    type DashboardRange,
    formatCount,
    formatUptime,
    mergeActivityTrend,
    operationLabel,
    resultTone,
    userLabel,
} from "./composables/_dashboard";
import { type DashboardTone, useDashboardTheme } from "./composables/_theme";

type InsightTone = {
    icon: ComponentType<IconProps>;
    label: string;
    tone: DashboardTone;
};

type RecentOperationRow = RecentOperation & {
    rowKey: string;
};

const INSIGHT_TONES: Record<string, InsightTone> = {
    CRITICAL: {
        icon: ShieldErrorIcon,
        label: "高危",
        tone: "red",
    },
    INFO: {
        icon: InfoCircleIcon,
        label: "提示",
        tone: "blue",
    },
    SUCCESS: {
        icon: CheckCircleIcon,
        label: "正常",
        tone: "green",
    },
    WARNING: {
        icon: ErrorTriangleIcon,
        label: "预警",
        tone: "warning",
    },
};

const RESULT_ICONS: Record<string, ComponentType<IconProps>> = {
    DENIED: ErrorCircleIcon,
    FAILURE: ErrorTriangleIcon,
    SUCCESS: CheckCircleIcon,
};

const JOURNEY_PAGE_SIZE = 100;

async function loadJourneySummaries(from: string): Promise<AuditJourneyPage> {
    const items: AuditJourney[] = [];
    let page = 1;
    let result: AuditJourneyPage;

    do {
        result = (
            await apiClient.auditLog.listAuditJourneys({
                from,
                page,
                size: JOURNEY_PAGE_SIZE,
                summary: true,
            })
        ).data;
        items.push(...(result.items ?? []));
        page++;
    } while (items.length < (result.total ?? 0) && (result.items?.length ?? 0) > 0);

    return { ...result, items };
}

function InsightCard({ insight }: { insight: DashboardInsight }) {
    const { token, tone } = useDashboardTheme();
    const config = INSIGHT_TONES[insight.severity] ?? INSIGHT_TONES.INFO;
    const Icon = config.icon;
    const colors = tone(config.tone);

    return (
        <div className="flex gap-3 rounded-2xl p-4" style={{ background: token.colorFillQuaternary }}>
            <span
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl"
                style={{ backgroundColor: colors.soft, color: colors.base }}
            >
                <Icon size={19} />
            </span>
            <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold" style={{ color: token.colorText }}>
                        {insight.title}
                    </span>
                    <span className="text-[11px] font-medium" style={{ color: colors.base }}>
                        {config.label}
                    </span>
                </div>
                <p className="mt-1 text-xs leading-5" style={{ color: token.colorTextSecondary }}>
                    {insight.description}
                </p>
            </div>
        </div>
    );
}

export function DashboardView() {
    const prompt = useMessagePrompt();
    const { token, tone } = useDashboardTheme();
    const [range, setRange] = useState<DashboardRange>(7);
    const [journeyAnchor, setJourneyAnchor] = useState(() => Date.now());
    const reportedOverviewError = useRef(false);
    const warnedTruncated = useRef(false);
    const reportedJourneyError = useRef(false);
    const journeyFrom = useMemo(
        () => new Date(journeyAnchor - range * 86_400_000).toISOString(),
        [journeyAnchor, range],
    );
    const overviewQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => {
            const response = await apiClient.dashboard.getDashboardOverview({ params: { days: range } });

            return response.data as unknown as DashboardData;
        },
        queryKey: ["console", "dashboard", range],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const journeysQuery = useQuery({
        queryFn: () => loadJourneySummaries(journeyFrom),
        queryKey: ["console", "dashboard", "journeys", range, journeyFrom],
        refetchInterval: 120_000,
        staleTime: 30_000,
    });
    const overview = overviewQuery.data;
    const trend = useMemo(() => (overview ? mergeActivityTrend(overview) : []), [overview]);

    useEffect(() => {
        if (overviewQuery.isError && !reportedOverviewError.current) {
            reportedOverviewError.current = true;
            prompt.error({ content: consoleErrorMessage(overviewQuery.error) });
        }

        if (!overviewQuery.isError) {
            reportedOverviewError.current = false;
        }
    }, [overviewQuery.error, overviewQuery.isError, prompt]);

    useEffect(() => {
        if (journeysQuery.data?.truncated) {
            if (!warnedTruncated.current) {
                warnedTruncated.current = true;
                prompt.warning({ content: "链路数据达到扫描上限，请缩小时间范围。" });
            }

            return;
        }

        if (journeysQuery.data) {
            warnedTruncated.current = false;
        }
    }, [journeysQuery.data, prompt]);

    useEffect(() => {
        if (journeysQuery.isError && !reportedJourneyError.current) {
            reportedJourneyError.current = true;
            prompt.error({ content: consoleErrorMessage(journeysQuery.error) });
        }

        if (!journeysQuery.isError) {
            reportedJourneyError.current = false;
        }
    }, [journeysQuery.error, journeysQuery.isError, prompt]);

    if (overviewQuery.isPending) {
        return (
            <div className="space-y-4">
                <Skeleton theme="paragraph" />
                <Skeleton theme="paragraph" />
                <Skeleton theme="paragraph" />
            </div>
        );
    }

    if (overviewQuery.isError && !overview) {
        return (
            <Card bodyStyle={{ padding: 48 }}>
                <Empty
                    action={
                        <Button
                            icon={<RefreshIcon />}
                            onClick={() => {
                                void overviewQuery.refetch();
                            }}
                        >
                            重新加载
                        </Button>
                    }
                    description="概览数据加载失败"
                />
            </Card>
        );
    }

    if (!overview) {
        return <Empty description="暂无概览数据" />;
    }

    const metricMap = new Map((overview.metrics ?? []).map((metric) => [metric.key, metric]));
    const activeUsers = overview.activeUsers ?? [];
    const maxActiveOperations = Math.max(1, ...activeUsers.map((user) => user.operations ?? 0));
    const topOperations = overview.topOperations ?? [];
    const maxOperationCount = Math.max(1, ...topOperations.map((operation) => operation.count));
    const results = overview.resultDistribution ?? [];
    const resultTotal = results.reduce((sum, item) => sum + item.count, 0);
    const insights = overview.insights ?? [];
    const recentRows: RecentOperationRow[] = (overview.recentAuditLogs ?? []).map((record, index) => ({
        ...record,
        rowKey: record.auditId ?? `recent-${index}`,
    }));
    const recentColumns: PrimaryTableCol<RecentOperationRow>[] = [
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
            cell: ({ row }) => <span className="font-medium">{userLabel(row.operator)}</span>,
            colKey: "operator",
            title: "操作人",
            width: 140,
        },
        {
            cell: ({ row }) => operationLabel(row.operation),
            colKey: "operation",
            title: "操作",
            width: 120,
        },
        {
            cell: ({ row }) => row.resourceName ?? row.resourceType ?? "—",
            colKey: "resource",
            title: "资源",
        },
        {
            cell: ({ row }) => <ResultTag result={row.result} />,
            colKey: "result",
            title: "结果",
            width: 92,
        },
    ];
    const siteInfo = [
        { label: "站点名称", value: overview.globalInfo?.siteTitle ?? "—" },
        { label: "版本", value: overview.globalInfo?.version ?? "—" },
        {
            label: "站点地址",
            value: overview.globalInfo?.externalUrl ? (
                <Link className="break-all" href={overview.globalInfo.externalUrl} target="_blank">
                    {overview.globalInfo.externalUrl}
                </Link>
            ) : (
                "—"
            ),
        },
        { label: "时区", value: overview.globalInfo?.timezone ?? "—" },
        { label: "运行时长", value: formatUptime(overview.globalInfo?.uptimeSeconds) },
        { label: "用户组总数", value: formatCount(overview.totalUserGroups) },
        { label: "Webhook 总数", value: formatCount(overview.totalWebhooks) },
        { label: "数据时间", value: formatDateTime(overview.generatedAt) },
    ];

    return (
        <div className="space-y-4">
            <Card bodyStyle={{ padding: 16 }}>
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0 truncate text-lg font-semibold" style={{ color: token.colorText }}>
                        {overview.globalInfo?.siteTitle ?? "Pudans"}
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <Select
                            aria-label="统计范围"
                            className="w-full sm:w-36"
                            onChange={(value) => {
                                setRange(Number(value) as DashboardRange);
                                setJourneyAnchor(Date.now());
                            }}
                            options={DASHBOARD_RANGES}
                            value={range}
                        />
                        <Tooltip content="刷新数据">
                            <Button
                                aria-label="刷新数据"
                                icon={<RefreshIcon />}
                                loading={overviewQuery.isFetching}
                                onClick={() => {
                                    setJourneyAnchor(Date.now());
                                    void overviewQuery.refetch();
                                }}
                                shape="square"
                                variant="outline"
                            />
                        </Tooltip>
                    </div>
                </div>
            </Card>

            <Row gutter={[16, 16]}>
                {DASHBOARD_METRIC_ORDER.map((key) => {
                    const definition = DASHBOARD_METRICS[key];
                    const metric = metricMap.get(key);

                    if (!definition || !metric) {
                        return null;
                    }

                    return (
                        <Col key={key} md={6} sm={12} span={24} xl={3}>
                            <MetricCard definition={definition} metric={metric} />
                        </Col>
                    );
                })}

                <Col span={24} xl={14}>
                    <SectionCard title="身份活动趋势">
                        <ActivityTrendChart data={trend} />
                    </SectionCard>
                </Col>
                <Col span={24} xl={10}>
                    <SectionCard title="角色成员分布">
                        <RoleDistributionChart roles={overview.roleDistribution} />
                    </SectionCard>
                </Col>

                <Col md={12} span={24} xl={8}>
                    <SectionCard title="活跃用户排行">
                        {activeUsers.length === 0 ? (
                            <Empty description="暂无活跃用户" />
                        ) : (
                            <div className="space-y-4">
                                {activeUsers.map((user, index) => {
                                    const operations = user.operations ?? 0;
                                    const rankColor =
                                        [
                                            tone("gold").base,
                                            tone("geekblue").base,
                                            tone("volcano").base,
                                            tone("blue").base,
                                        ][index] ?? token.colorPrimary;

                                    return (
                                        <div key={`${user.name ?? "anonymous"}-${index}`}>
                                            <div className="mb-2 flex items-center gap-3">
                                                <span
                                                    className="flex h-7 w-7 shrink-0 items-center justify-center rounded-xl text-xs font-semibold text-[var(--td-text-color-anti)]"
                                                    style={{ backgroundColor: rankColor }}
                                                >
                                                    {index + 1}
                                                </span>
                                                <span
                                                    className="min-w-0 flex-1 truncate text-sm font-medium"
                                                    style={{ color: token.colorText }}
                                                >
                                                    {userLabel(user.name)}
                                                </span>
                                                <span
                                                    className="text-xs tabular-nums"
                                                    style={{ color: token.colorTextSecondary }}
                                                >
                                                    {formatCount(operations)}
                                                </span>
                                            </div>
                                            <Progress
                                                color={index < 3 ? token.colorPrimary : token.colorPrimaryBorder}
                                                label={false}
                                                percentage={Math.max(4, (operations / maxActiveOperations) * 100)}
                                                size="small"
                                                trackColor={token.colorFillSecondary}
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </SectionCard>
                </Col>
                <Col md={12} span={24} xl={8}>
                    <SectionCard title="高频操作">
                        {topOperations.length === 0 ? (
                            <Empty description="暂无操作数据" />
                        ) : (
                            <div className="space-y-4">
                                {topOperations.map((operation, index) => (
                                    <div key={operation.operation}>
                                        <div className="mb-2 flex items-center gap-3">
                                            <span
                                                className="w-4 text-xs font-semibold tabular-nums"
                                                style={{ color: token.colorTextTertiary }}
                                            >
                                                {index + 1}
                                            </span>
                                            <span
                                                className="min-w-0 flex-1 truncate text-sm font-medium"
                                                style={{ color: token.colorText }}
                                            >
                                                {operationLabel(operation.operation)}
                                            </span>
                                            <span
                                                className="text-xs tabular-nums"
                                                style={{ color: token.colorTextSecondary }}
                                            >
                                                {formatCount(operation.count)}
                                            </span>
                                        </div>
                                        <Progress
                                            color={
                                                [tone("gold").base, tone("orange").base, tone("volcano").base][index] ??
                                                token.colorPrimaryBorder
                                            }
                                            label={false}
                                            percentage={Math.max(4, (operation.count / maxOperationCount) * 100)}
                                            size="small"
                                            trackColor={token.colorFillSecondary}
                                        />
                                    </div>
                                ))}
                            </div>
                        )}
                    </SectionCard>
                </Col>
                <Col md={24} span={24} xl={8}>
                    <SectionCard title="审计结果分布">
                        {results.length === 0 ? (
                            <Empty description="暂无审计结果" />
                        ) : (
                            <div className="space-y-5">
                                {results.map((item) => {
                                    const result = resultTone(item.result);
                                    const colors = tone(result.tone);
                                    const Icon = RESULT_ICONS[item.result] ?? ErrorTriangleIcon;
                                    const percent = resultTotal === 0 ? 0 : (item.count / resultTotal) * 100;

                                    return (
                                        <div key={item.result}>
                                            <div className="mb-2 flex items-center gap-3">
                                                <span
                                                    className="flex h-8 w-8 items-center justify-center rounded-xl"
                                                    style={{
                                                        backgroundColor: colors.soft,
                                                        color: colors.base,
                                                    }}
                                                >
                                                    <Icon size={16} />
                                                </span>
                                                <span
                                                    className="flex-1 text-sm"
                                                    style={{ color: token.colorTextSecondary }}
                                                >
                                                    {result.label}
                                                </span>
                                                <span
                                                    className="text-xs font-semibold tabular-nums"
                                                    style={{ color: token.colorText }}
                                                >
                                                    {formatCount(item.count)}
                                                </span>
                                                <span
                                                    className="w-12 text-right text-xs tabular-nums"
                                                    style={{ color: token.colorTextTertiary }}
                                                >
                                                    {percent.toFixed(1)}%
                                                </span>
                                            </div>
                                            <Progress
                                                color={colors.base}
                                                label={false}
                                                percentage={percent}
                                                size="small"
                                                trackColor={token.colorFillSecondary}
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </SectionCard>
                </Col>

                <Col span={24}>
                    <SectionCard title="用户链路">
                        <RecentUserJourneys
                            error={journeysQuery.isError}
                            from={journeyFrom}
                            isPending={journeysQuery.isPending}
                            journeys={journeysQuery.data?.items ?? []}
                            onRetry={() => {
                                void journeysQuery.refetch();
                            }}
                        />
                    </SectionCard>
                </Col>

                <Col span={24} xl={16}>
                    <SectionCard title="最近操作">
                        <Table
                            columns={recentColumns}
                            data={recentRows}
                            hover
                            rowKey="rowKey"
                            size="small"
                            tableLayout="auto"
                        />
                    </SectionCard>
                </Col>
                <Col span={24} xl={8}>
                    <SectionCard title="安全诊断">
                        {insights.length === 0 ? (
                            <Empty description="暂无诊断项" />
                        ) : (
                            <div className="space-y-3">
                                {insights.map((insight) => (
                                    <InsightCard insight={insight} key={insight.title} />
                                ))}
                            </div>
                        )}
                    </SectionCard>
                </Col>

                <Col span={24}>
                    <SectionCard title="站点信息">
                        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-4">
                            {siteInfo.map((item) => (
                                <StatTile key={item.label} label={item.label} value={item.value} />
                            ))}
                        </div>
                    </SectionCard>
                </Col>
            </Row>
        </div>
    );
}
