"use client";

import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";

import { type AuditSessionSummary, apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { RefreshIcon } from "tdesign-icons-react";
import { Breadcrumb, Button, Card, Col, DateRangePicker, Empty, Row, Skeleton } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { RankedBarList } from "../../../components/ranked-bar-list";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCount } from "../../../composables/_format";
import { ActivityTrendChart } from "./components/activity-trend-chart";
import { AuditResultDistribution } from "./components/audit-result-distribution";
import { BoardCard } from "./components/board-card";
import { MetricSnapshot } from "./components/metric-snapshot";
import { OperationRankingTable } from "./components/operation-ranking-table";
import { RecentOperationsTable } from "./components/recent-operations-table";
import { RecentUserJourneys } from "./components/recent-user-journeys";
import { RoleDistributionChart } from "./components/role-distribution-chart";
import { SecurityInsights } from "./components/security-insights";
import { SecurityTrendChart } from "./components/security-trend-chart";
import { SiteInfoGrid } from "./components/site-info-grid";
import {
    DASHBOARD_METRICS,
    type DashboardData,
    type DashboardDateRange,
    PRIMARY_METRIC_KEYS,
    dashboardDateValue,
    dashboardRangeBounds,
    dashboardRangeDays,
    dashboardRangeForDays,
    mergeActivityTrend,
    metricCaption,
    userLabel,
} from "./composables/_dashboard";

const { BreadcrumbItem } = Breadcrumb;

async function loadJourneySessions(
    from: string,
    to: string,
): Promise<{ items: AuditSessionSummary[]; truncated: boolean }> {
    const items: AuditSessionSummary[] = [];
    let page = 1;
    let truncated = false;

    while (true) {
        const response = await apiClient.auditLog.listAuditSessions({
            from,
            page,
            size: 100,
            to,
        });
        const batch = response.data.items ?? [];
        items.push(...batch);
        truncated = truncated || Boolean(response.data.truncated);
        if (batch.length === 0 || items.length >= (response.data.total ?? items.length)) {
            break;
        }
        page += 1;
    }

    return { items, truncated };
}

type DashboardPageFrameProps = {
    actions?: ReactNode;
    children: ReactNode;
};

function DashboardPageFrame({ actions, children }: DashboardPageFrameProps) {
    return (
        <div className="overflow-x-hidden">
            <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <Breadcrumb>
                    <BreadcrumbItem>概览</BreadcrumbItem>
                    <BreadcrumbItem>面板首页</BreadcrumbItem>
                </Breadcrumb>
                {actions}
            </div>
            {children}
        </div>
    );
}

function DashboardSkeleton() {
    return (
        <>
            <Row align="stretch" gutter={[16, 16]}>
                {PRIMARY_METRIC_KEYS.map((key) => (
                    <Col key={key} sm={6} xl={3} xs={12}>
                        <Card bordered={false} bodyStyle={{ minHeight: 142 }}>
                            <Skeleton theme="paragraph" />
                        </Card>
                    </Col>
                ))}
            </Row>
            <div className="mt-4">
                <Row align="stretch" gutter={[16, 16]}>
                    <Col xl={9} xs={12}>
                        <Card bordered={false} bodyStyle={{ minHeight: 408 }}>
                            <Skeleton theme="paragraph" />
                        </Card>
                    </Col>
                    <Col xl={3} xs={12}>
                        <Card bordered={false} bodyStyle={{ minHeight: 408 }}>
                            <Skeleton theme="paragraph" />
                        </Card>
                    </Col>
                </Row>
            </div>
        </>
    );
}

export function DashboardView() {
    const prompt = useMessagePrompt();
    const [range, setRange] = useState<DashboardDateRange>(() => dashboardRangeForDays(7));
    const reportedOverviewError = useRef(false);
    const rangeBounds = useMemo(() => dashboardRangeBounds(range), [range]);
    const overviewQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => {
            const response = await apiClient.dashboard.getDashboardOverview({
                days: range.days,
                to: rangeBounds.to,
            });

            return response.data as unknown as DashboardData;
        },
        queryKey: ["console", "dashboard", range.days, range.end, range.start, rangeBounds.to],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const sessionsQuery = useQuery({
        queryFn: () => loadJourneySessions(rangeBounds.from, rangeBounds.to),
        queryKey: ["console", "dashboard", "sessions", range.end, range.start, rangeBounds.from, rangeBounds.to],
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

    const today = dashboardDateValue(new Date());
    const handleRangeChange = (value: Array<Date | number | string>) => {
        const [nextStart, nextEnd] = value;
        if (!nextStart || !nextEnd) {
            return;
        }

        const start = dashboardDateValue(nextStart);
        const end = dashboardDateValue(nextEnd);
        const days = dashboardRangeDays(start, end);
        if (days < 1 || days > 90) {
            prompt.warning({ content: "统计范围最多支持 90 天" });
            return;
        }

        setRange({ days, end, start });
    };
    const disableFutureDate = ({
        date,
        partial,
    }: {
        date?: Array<Date | number | string>;
        partial?: "end" | "start";
    }) => {
        if (!Array.isArray(date) || !partial) {
            return false;
        }

        const current = date[partial === "start" ? 0 : 1];
        if (!current) {
            return false;
        }

        const currentDay = dashboardDateValue(current);
        if (currentDay > today) {
            return true;
        }

        const other = date[partial === "start" ? 1 : 0];
        if (!other) {
            return false;
        }

        const otherDay = dashboardDateValue(other);
        const start = partial === "start" ? currentDay : otherDay;
        const end = partial === "start" ? otherDay : currentDay;

        return end < start || dashboardRangeDays(start, end) > 90;
    };
    const refresh = () => {
        void overviewQuery.refetch();
        void sessionsQuery.refetch();
    };
    const headerActions = (
        <div className="flex w-full items-center gap-2 sm:w-auto">
            <DateRangePicker
                aria-label="统计日期范围"
                className="min-w-0 flex-1 sm:w-[280px] sm:flex-none"
                clearable={false}
                disableDate={disableFutureDate}
                format="YYYY-MM-DD"
                onChange={handleRangeChange}
                placeholder={["开始日期", "结束日期"]}
                separator="–"
                value={[range.start, range.end]}
                valueType="YYYY-MM-DD"
            />
            <Button
                aria-label="刷新数据"
                icon={<RefreshIcon />}
                loading={overviewQuery.isFetching}
                onClick={refresh}
                shape="square"
                variant="text"
            />
        </div>
    );

    if (overviewQuery.isPending) {
        return (
            <DashboardPageFrame actions={headerActions}>
                <DashboardSkeleton />
            </DashboardPageFrame>
        );
    }

    if (overviewQuery.isError && !overview) {
        return (
            <DashboardPageFrame actions={headerActions}>
                <Card bordered={false} bodyStyle={{ padding: 48 }}>
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
            </DashboardPageFrame>
        );
    }

    if (!overview) {
        return (
            <DashboardPageFrame actions={headerActions}>
                <Card bordered={false} bodyStyle={{ padding: 48 }}>
                    <Empty description="暂无概览数据" />
                </Card>
            </DashboardPageFrame>
        );
    }

    const metricMap = new Map((overview.metrics ?? []).map((metric) => [metric.key, metric]));
    const activeUsers = (overview.activeUsers ?? []).slice(0, 5);
    const topOperations = (overview.topOperations ?? []).slice(0, 5);
    const recentOperations = (overview.recentAuditLogs ?? []).slice(0, 5);
    const insightCount = (overview.insights ?? []).length;
    const resultTotal = (overview.resultDistribution ?? []).reduce((sum, item) => sum + (item.count ?? 0), 0);
    const operationTotal =
        metricMap.get("operations")?.value ?? topOperations.reduce((sum, item) => sum + item.count, 0);
    const activeUserItems = activeUsers.map((user, index) => ({
        key: `${user.name ?? "anonymous"}-${index}`,
        label: userLabel(user.name),
        value: user.operations ?? 0,
        valueLabel: "次操作",
    }));

    return (
        <DashboardPageFrame actions={headerActions}>
            <Row align="stretch" gutter={[16, 16]}>
                {PRIMARY_METRIC_KEYS.map((key, index) => {
                    const definition = DASHBOARD_METRICS[key];
                    const metric = metricMap.get(key);

                    if (!definition || !metric) {
                        return null;
                    }

                    return (
                        <Col key={key} sm={6} xl={3} xs={12}>
                            <BoardCard
                                caption={metricCaption(key, overview, metricMap)}
                                dark={index === 0}
                                definition={definition}
                                metric={metric}
                            />
                        </Col>
                    );
                })}
            </Row>

            <div className="mt-4">
                <Row align="stretch" gutter={[16, 16]}>
                    <Col xl={9} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`操作、登录与异常 · 近 ${formatCount(overview.rangeDays ?? range.days)} 天`}
                            title="身份活动趋势"
                        >
                            <ActivityTrendChart data={trend} />
                        </Card>
                    </Col>
                    <Col xl={3} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`共 ${formatCount(overview.totalRoles)} 个角色`}
                            title="角色成员分布"
                        >
                            <RoleDistributionChart roles={overview.roleDistribution} />
                        </Card>
                    </Col>
                </Row>
            </div>

            <div className="mt-4">
                <Row align="stretch" gutter={[16, 16]}>
                    <Col lg={4} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`前 ${formatCount(activeUsers.length)} 位`}
                            title="活跃用户排行"
                        >
                            <RankedBarList emptyText="暂无活跃用户" items={activeUserItems} />
                        </Card>
                    </Col>
                    <Col lg={4} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`共 ${formatCount(resultTotal)} 次操作`}
                            title="审计结果分布"
                        >
                            <AuditResultDistribution results={overview.resultDistribution} />
                        </Card>
                    </Col>
                    <Col lg={4} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`${formatCount(insightCount)} 项诊断`}
                            title="安全诊断"
                        >
                            <SecurityInsights insights={overview.insights} />
                        </Card>
                    </Col>
                </Row>
            </div>

            <div className="mt-4">
                <Row align="stretch" gutter={[16, 16]}>
                    <Col lg={6} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`前 ${formatCount(topOperations.length)} 项`}
                            title="高频操作"
                        >
                            <OperationRankingTable items={topOperations} total={operationTotal} />
                        </Card>
                    </Col>
                    <Col lg={6} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`最近 ${formatCount(recentOperations.length)} 条`}
                            title="最近操作"
                        >
                            <RecentOperationsTable items={recentOperations} />
                        </Card>
                    </Col>
                </Row>
            </div>

            <div className="mt-4">
                <Row align="stretch" gutter={[16, 16]}>
                    <Col xl={8} xs={12}>
                        <Card
                            bordered={false}
                            className="h-full"
                            subtitle={`风险规则 ${formatCount(overview.totalRiskRules)} 条 · Webhook ${formatCount(overview.totalWebhooks)} 个`}
                            title="风险与集成趋势"
                        >
                            <SecurityTrendChart overview={overview} />
                        </Card>
                    </Col>
                    <Col xl={4} xs={12}>
                        <Card bordered={false} className="h-full" subtitle="与上一周期对比" title="本期指标">
                            <MetricSnapshot metrics={metricMap} />
                        </Card>
                    </Col>
                </Row>
            </div>

            <div className="mt-4">
                <Card bordered={false} subtitle="运行环境与数据快照" title="站点信息">
                    <SiteInfoGrid overview={overview} />
                </Card>
            </div>

            <div className="mt-4">
                <Card bordered={false} title="最近用户追溯" subtitle="最近用户的页面路径与会话摘要">
                    <RecentUserJourneys
                        error={sessionsQuery.isError}
                        isPending={sessionsQuery.isPending}
                        onRetry={() => {
                            void sessionsQuery.refetch();
                        }}
                        sessions={sessionsQuery.data?.items ?? []}
                        truncated={sessionsQuery.data?.truncated ?? false}
                    />
                </Card>
            </div>
        </DashboardPageFrame>
    );
}
