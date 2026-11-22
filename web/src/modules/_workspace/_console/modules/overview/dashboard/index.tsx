"use client";

import { type ReactNode, useEffect, useMemo, useState } from "react";

import { type AuditJourney, type AuditJourneyPage, type RecentOperation, apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Empty, Progress, Segmented, Skeleton, Table, type TableProps, Tag, Tooltip } from "antd";
import {
    AlertTriangle,
    ArrowUpRight,
    CheckCircle2,
    ChevronRight,
    CircleAlert,
    Info,
    type LucideIcon,
    RefreshCw,
    ShieldAlert,
    ShieldCheck,
    Sparkles,
} from "lucide-react";

import { BentoGrid } from "@/components/layouts/bento-grid";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ResultTag } from "../../../components/result-tag";
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

type PanelProps = {
    children: ReactNode;
    className?: string;
    extra?: ReactNode;
    subtitle?: string;
    title: string;
};

type InsightTone = {
    icon: LucideIcon;
    label: string;
    tone: DashboardTone;
};

const INSIGHT_TONES: Record<string, InsightTone> = {
    CRITICAL: {
        icon: ShieldAlert,
        label: "高危",
        tone: "red",
    },
    INFO: {
        icon: Info,
        label: "提示",
        tone: "blue",
    },
    SUCCESS: {
        icon: ShieldCheck,
        label: "正常",
        tone: "green",
    },
    WARNING: {
        icon: AlertTriangle,
        label: "预警",
        tone: "gold",
    },
};

const RESULT_ICONS: Record<string, LucideIcon> = {
    DENIED: CircleAlert,
    FAILURE: AlertTriangle,
    SUCCESS: CheckCircle2,
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

function Panel({ children, className, extra, subtitle, title }: PanelProps) {
    const { token } = useDashboardTheme();

    return (
        <Card
            className={`h-full !rounded-[20px] sm:!rounded-[28px] ${className ?? ""}`}
            style={{
                background: token.colorBgContainer,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowTertiary,
            }}
            styles={{ body: { padding: 0 } }}
        >
            <div className="flex flex-col gap-3 px-4 pt-4 sm:flex-row sm:items-start sm:justify-between sm:px-5 sm:pt-5">
                <div>
                    <div className="text-base font-semibold tracking-tight" style={{ color: token.colorText }}>
                        {title}
                    </div>
                    {subtitle ? (
                        <div className="mt-1 text-xs leading-5" style={{ color: token.colorTextSecondary }}>
                            {subtitle}
                        </div>
                    ) : null}
                </div>
                {extra}
            </div>
            <div className="p-4 sm:p-5">{children}</div>
        </Card>
    );
}

function InsightCard({ insight }: { insight: DashboardInsight }) {
    const { token, tone } = useDashboardTheme();
    const config = INSIGHT_TONES[insight.severity] ?? INSIGHT_TONES.INFO;
    const Icon = config.icon;
    const colors = tone(config.tone);

    return (
        <div
            className="flex gap-3 rounded-2xl border p-4"
            style={{
                background: token.colorFillQuaternary,
                borderColor: token.colorBorderSecondary,
            }}
        >
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
                    <span
                        className="rounded-full px-2 py-0.5 text-[10px] font-semibold"
                        style={{ backgroundColor: colors.soft, color: colors.base }}
                    >
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

function StatPill({ label, value }: { label: string; value: ReactNode }) {
    const { token } = useDashboardTheme();

    return (
        <div
            className="rounded-2xl border px-4 py-3"
            style={{
                background: token.colorFillQuaternary,
                borderColor: token.colorBorderSecondary,
            }}
        >
            <div className="text-[11px]" style={{ color: token.colorTextSecondary }}>
                {label}
            </div>
            <div
                className="mt-1 text-sm font-semibold [overflow-wrap:anywhere] break-words"
                style={{ color: token.colorText }}
            >
                {value}
            </div>
        </div>
    );
}

export function DashboardView() {
    const prompt = useMessagePrompt();
    const { token, tone } = useDashboardTheme();
    const [range, setRange] = useState<DashboardRange>(7);
    const [journeyAnchor, setJourneyAnchor] = useState(() => Date.now());
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
        if (!overviewQuery.isError) {
            return;
        }

        prompt.error({
            content: consoleErrorMessage(overviewQuery.error),
            key: "dashboard-error",
        });
    }, [overviewQuery.error, overviewQuery.isError, prompt]);

    if (overviewQuery.isPending) {
        return <Skeleton active paragraph={{ rows: 12 }} />;
    }

    if (overviewQuery.isError && !overview) {
        return (
            <Card
                className="!rounded-[28px]"
                style={{ borderColor: token.colorBorderSecondary }}
                styles={{ body: { padding: 48 } }}
            >
                <Empty description="概览数据加载失败">
                    <Button icon={<RefreshCw size={14} />} onClick={() => void overviewQuery.refetch()}>
                        重新加载
                    </Button>
                </Empty>
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
    const recentColumns: TableProps<RecentOperation>["columns"] = [
        {
            dataIndex: "timestamp",
            key: "timestamp",
            render: (value: string) => (
                <span className="text-xs whitespace-nowrap tabular-nums" style={{ color: token.colorTextSecondary }}>
                    {formatDateTime(value)}
                </span>
            ),
            title: "时间",
            width: 168,
        },
        {
            dataIndex: "operator",
            key: "operator",
            render: (value: string) => <span className="font-medium">{userLabel(value)}</span>,
            title: "操作人",
            width: 140,
        },
        {
            dataIndex: "operation",
            key: "operation",
            render: (value: string) => operationLabel(value),
            title: "操作",
            width: 120,
        },
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
            width: 92,
        },
    ];
    const siteInfo = [
        { label: "站点名称", value: overview.globalInfo?.siteTitle ?? "—" },
        { label: "版本", value: overview.globalInfo?.version ?? "—" },
        {
            label: "站点地址",
            value: overview.globalInfo?.externalUrl ? (
                <a
                    className="break-all hover:underline"
                    href={overview.globalInfo.externalUrl}
                    rel="noreferrer"
                    style={{ color: token.colorLink }}
                    target="_blank"
                >
                    {overview.globalInfo.externalUrl}
                </a>
            ) : (
                "—"
            ),
        },
        { label: "时区", value: overview.globalInfo?.timezone ?? "—" },
        { label: "运行时长", value: formatUptime(overview.globalInfo?.uptimeSeconds) },
        { label: "数据时间", value: formatDateTime(overview.generatedAt) },
        { label: "用户组总数", value: formatCount(overview.totalUserGroups) },
        { label: "Webhook 总数", value: formatCount(overview.totalWebhooks) },
    ];

    return (
        <BentoGrid className="bento-grid--dashboard">
            <section
                className="relative overflow-hidden rounded-[24px] border p-5 sm:rounded-[32px] sm:p-8 md:col-span-2 xl:col-span-12"
                style={{
                    background: `linear-gradient(135deg, ${token.colorBgContainer} 0%, ${token.colorPrimaryBg} 48%, ${token.colorInfoBg} 100%)`,
                    borderColor: token.colorBorderSecondary,
                    boxShadow: token.boxShadowSecondary,
                }}
            >
                <div
                    className="pointer-events-none absolute -top-24 -right-20 h-72 w-72 rounded-full blur-3xl"
                    style={{ background: token.colorPrimaryBorder, opacity: 0.32 }}
                />
                <div
                    className="pointer-events-none absolute -bottom-32 left-1/3 h-64 w-64 rounded-full blur-3xl"
                    style={{ background: token.colorInfoBorder, opacity: 0.24 }}
                />
                <div className="relative flex flex-col gap-6 xl:flex-row xl:items-end xl:justify-between">
                    <div>
                        <div
                            className="flex items-center gap-2 text-[11px] font-semibold tracking-[0.24em] uppercase"
                            style={{ color: token.colorPrimary }}
                        >
                            <Sparkles size={13} />
                            PUDANS 身份云
                        </div>
                        <p
                            className="mt-3 max-w-2xl text-2xl font-semibold tracking-tight sm:text-3xl"
                            style={{ color: token.colorText }}
                        >
                            统一身份，洞察每一次访问
                        </p>
                        <p className="mt-2 max-w-2xl text-sm leading-6" style={{ color: token.colorTextSecondary }}>
                            统一管理用户、用户组、角色与权限，实时掌握登录、审计和安全风险态势。
                        </p>
                        <div className="mt-4 flex flex-wrap items-center gap-2">
                            <Tag color="geekblue" variant="filled">
                                {overview.globalInfo?.siteTitle ?? "Pudans"}
                            </Tag>
                            <Tag variant="filled">版本 {overview.globalInfo?.version ?? "—"}</Tag>
                            <Tag variant="filled">时区 {overview.globalInfo?.timezone ?? "—"}</Tag>
                            <Tag color="orange" variant="filled">
                                今日操作 {formatCount(overview.todayOperations)}
                            </Tag>
                        </div>
                    </div>
                    <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center xl:flex-col xl:items-end">
                        <Segmented
                            onChange={(value) => {
                                setRange(value as DashboardRange);
                                setJourneyAnchor(Date.now());
                            }}
                            options={DASHBOARD_RANGES}
                            value={range}
                        />
                        <div className="flex items-center gap-2">
                            <span
                                className="flex items-center gap-2 text-xs"
                                style={{ color: token.colorTextSecondary }}
                            >
                                <span
                                    className={`h-2 w-2 rounded-full ${
                                        overviewQuery.isFetching ? "animate-pulse" : ""
                                    }`}
                                    style={{ background: token.colorSuccess }}
                                />
                                {overviewQuery.isFetching ? "数据同步中…" : "数据已同步"}
                            </span>
                            <Tooltip title="刷新数据">
                                <Button
                                    aria-label="刷新数据"
                                    icon={<RefreshCw size={14} />}
                                    loading={overviewQuery.isFetching}
                                    onClick={() => {
                                        setJourneyAnchor(Date.now());
                                        void overviewQuery.refetch();
                                    }}
                                    shape="circle"
                                    type="text"
                                />
                            </Tooltip>
                        </div>
                        <div
                            className="text-left text-[11px] leading-5 xl:text-right"
                            style={{ color: token.colorTextSecondary }}
                        >
                            数据更新于 {formatDateTime(overview.generatedAt)}
                            <br />
                            统计范围：近 {overview.rangeDays} 天
                        </div>
                    </div>
                </div>
            </section>

            {DASHBOARD_METRIC_ORDER.map((key) => {
                const definition = DASHBOARD_METRICS[key];
                const metric = metricMap.get(key);

                if (!definition || !metric) {
                    return null;
                }

                return (
                    <div className="md:col-span-1 xl:col-span-3" key={key}>
                        <MetricCard definition={definition} metric={metric} />
                    </div>
                );
            })}

            <div className="md:col-span-2 xl:col-span-7">
                <Panel
                    extra={
                        <span
                            className="rounded-full px-3 py-1 text-[11px] font-medium"
                            style={{
                                background: token.colorFillTertiary,
                                color: token.colorTextSecondary,
                            }}
                        >
                            操作 / 登录 / 异常
                        </span>
                    }
                    subtitle={`近 ${overview.rangeDays} 天全平台身份活动变化，数据来自真实审计记录。`}
                    title="身份活动趋势"
                >
                    <ActivityTrendChart data={trend} />
                </Panel>
            </div>
            <div className="md:col-span-2 xl:col-span-5">
                <Panel subtitle="按用户组继承关系汇总的成员分布。" title="角色成员分布">
                    <RoleDistributionChart roles={overview.roleDistribution} />
                </Panel>
            </div>

            <div className="md:col-span-1 xl:col-span-4">
                <Panel
                    extra={
                        <span className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                            按操作次数
                        </span>
                    }
                    title="活跃用户排行"
                >
                    {activeUsers.length === 0 ? (
                        <Empty description="暂无活跃用户" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                        <div className="space-y-4">
                            {activeUsers.map((user, index) => {
                                const operations = user.operations ?? 0;
                                const rankColor =
                                    [tone("gold").base, tone("geekblue").base, tone("volcano").base, tone("blue").base][
                                        index
                                    ] ?? token.colorPrimary;

                                return (
                                    <div key={`${user.name ?? "anonymous"}-${index}`}>
                                        <div className="flex items-center gap-3">
                                            <span
                                                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-xl text-xs font-semibold text-white"
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
                                                {formatCount(operations)} 次
                                            </span>
                                        </div>
                                        <Progress
                                            percent={Math.max(4, (operations / maxActiveOperations) * 100)}
                                            showInfo={false}
                                            size="small"
                                            strokeColor={index < 3 ? token.colorPrimary : token.colorPrimaryBorder}
                                            railColor={token.colorFillSecondary}
                                        />
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </Panel>
            </div>
            <div className="md:col-span-1 xl:col-span-4">
                <Panel
                    extra={
                        <span className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                            共 {topOperations.length} 项
                        </span>
                    }
                    subtitle="审计操作类型热度。"
                    title="高频操作"
                >
                    {topOperations.length === 0 ? (
                        <Empty description="暂无操作数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                        <div className="space-y-4">
                            {topOperations.map((operation, index) => (
                                <div key={operation.operation}>
                                    <div className="flex items-center gap-3">
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
                                        percent={Math.max(4, (operation.count / maxOperationCount) * 100)}
                                        showInfo={false}
                                        size="small"
                                        strokeColor={
                                            [tone("gold").base, tone("orange").base, tone("volcano").base][index] ??
                                            token.colorPrimaryBorder
                                        }
                                        railColor={token.colorFillSecondary}
                                    />
                                </div>
                            ))}
                        </div>
                    )}
                </Panel>
            </div>
            <div className="md:col-span-2 xl:col-span-4">
                <Panel
                    extra={
                        <Tag color="blue" variant="filled">
                            共 {formatCount(resultTotal)} 次
                        </Tag>
                    }
                    subtitle="成功、失败与拒绝操作的占比。"
                    title="审计结果分布"
                >
                    {results.length === 0 ? (
                        <Empty description="暂无审计结果" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                        <div className="space-y-5">
                            {results.map((item) => {
                                const result = resultTone(item.result);
                                const colors = tone(result.tone);
                                const Icon = RESULT_ICONS[item.result] ?? AlertTriangle;
                                const percent = resultTotal === 0 ? 0 : (item.count / resultTotal) * 100;

                                return (
                                    <div key={item.result}>
                                        <div className="flex items-center gap-3">
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
                                            percent={percent}
                                            showInfo={false}
                                            size="small"
                                            strokeColor={colors.base}
                                            railColor={token.colorFillSecondary}
                                        />
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </Panel>
            </div>

            <section
                className="rounded-[24px] border p-4 sm:rounded-[32px] sm:p-5 md:col-span-2 xl:col-span-12"
                style={{
                    background: token.colorBgContainer,
                    borderColor: token.colorBorderSecondary,
                    boxShadow: token.boxShadowTertiary,
                }}
            >
                <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <div className="text-base font-semibold tracking-tight" style={{ color: token.colorText }}>
                            最近链路回溯
                        </div>
                        <div className="mt-1 text-xs" style={{ color: token.colorTextSecondary }}>
                            近 {overview.rangeDays} 天用户、会话、入口、出口与操作类型的完整关系，数据来自真实审计链路。
                        </div>
                    </div>
                    <div
                        className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px]"
                        style={{ color: token.colorTextTertiary }}
                    >
                        <span>共 {formatCount(journeysQuery.data?.total)} 位用户</span>
                        <ChevronRight size={12} />
                        <span>{formatCount(journeysQuery.data?.pageViews)} 次页面访问</span>
                        <ChevronRight size={12} />
                        <span>{formatCount(journeysQuery.data?.actionCount)} 次操作</span>
                    </div>
                </div>
                {journeysQuery.data?.truncated ? (
                    <Alert
                        className="mb-4"
                        description="当前只包含后端扫描上限内的用户与会话，请缩小时间范围后重新查看。"
                        message="链路数据达到 20,000 条审计记录扫描上限"
                        showIcon
                        type="warning"
                    />
                ) : null}
                <RecentUserJourneys
                    error={journeysQuery.isError}
                    from={journeyFrom}
                    isPending={journeysQuery.isPending}
                    journeys={journeysQuery.data?.items ?? []}
                    onRetry={() => void journeysQuery.refetch()}
                />
            </section>

            <div className="md:col-span-2 xl:col-span-8">
                <Panel
                    extra={
                        <span
                            className="flex items-center gap-1 text-[11px] font-medium"
                            style={{ color: token.colorPrimary }}
                        >
                            最近 5 条
                            <ArrowUpRight size={12} />
                        </span>
                    }
                    subtitle="平台最近产生的真实操作记录。"
                    title="最近操作"
                >
                    <Table
                        columns={recentColumns}
                        dataSource={overview.recentAuditLogs ?? []}
                        pagination={false}
                        rowKey={(record) => record.auditId ?? ""}
                        scroll={{ x: "max-content" }}
                        size="small"
                    />
                </Panel>
            </div>
            <div className="md:col-span-2 xl:col-span-4">
                <Panel subtitle="基于当前审计、封禁与用户组数据生成。" title="安全诊断与风险提醒">
                    {insights.length === 0 ? (
                        <Empty description="暂无诊断项" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                        <div className="space-y-3">
                            {insights.map((insight) => (
                                <InsightCard insight={insight} key={insight.title} />
                            ))}
                        </div>
                    )}
                </Panel>
            </div>

            <div className="md:col-span-2 xl:col-span-12">
                <Panel subtitle="当前实例的运行环境与资源概况。" title="站点信息">
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-4">
                        {siteInfo.map((item) => (
                            <StatPill key={item.label} label={item.label} value={item.value} />
                        ))}
                    </div>
                </Panel>
            </div>
        </BentoGrid>
    );
}
