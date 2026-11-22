"use client";

import { useEffect } from "react";

import { type DashboardOverview, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Card, Col, Descriptions, Empty, Flex, List, Progress, Row, Skeleton, Statistic, Table, Tag } from "antd";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ResultTag } from "../../../components/result-tag";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime, formatDuration } from "../../../composables/_format";

export function DashboardView() {
    const prompt = useMessagePrompt();
    const overviewQuery = useQuery({
        queryKey: ["console", "dashboard"],
        queryFn: async () => (await apiClient.dashboard.getDashboardOverview()).data,
    });
    const overview: DashboardOverview | undefined = overviewQuery.data;

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
        return <Skeleton active />;
    }

    if (overviewQuery.isError) {
        return <Empty description="概览数据加载失败" />;
    }

    if (!overview) {
        return <Empty description="暂无概览数据" />;
    }

    const stats = [
        { title: "用户总数", value: overview.totalUsers ?? 0 },
        { title: "角色总数", value: overview.totalRoles ?? 0 },
        { title: "用户组总数", value: overview.totalUserGroups ?? 0 },
        { title: "Webhook 总数", value: overview.totalWebhooks ?? 0 },
        { title: "今日操作", value: overview.todayOperations ?? 0 },
        { title: "风险规则", value: overview.totalRiskRules ?? 0 },
    ];
    const roleDistribution = overview.roleDistribution ?? [];
    const maxRoleUsers = Math.max(1, ...roleDistribution.map((item) => item.users ?? 0));

    return (
        <Flex vertical gap={16}>
            <Row gutter={[16, 16]}>
                {stats.map((item) => (
                    <Col key={item.title} lg={8} md={12} xs={24}>
                        <Card>
                            <Statistic title={item.title} value={item.value} />
                        </Card>
                    </Col>
                ))}
            </Row>

            <Row gutter={[16, 16]}>
                <Col lg={16} xs={24}>
                    <Card title="登录趋势">
                        {(overview.loginTrend ?? []).length === 0 ? (
                            <Empty description="暂无趋势数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                        ) : (
                            <div className="h-64 w-full">
                                <ResponsiveContainer height="100%" width="100%">
                                    <AreaChart data={overview.loginTrend}>
                                        <CartesianGrid strokeDasharray="3 3" />
                                        <XAxis dataKey="date" />
                                        <YAxis allowDecimals={false} />
                                        <Tooltip />
                                        <Area
                                            dataKey="count"
                                            fill="#3b82f6"
                                            fillOpacity={0.18}
                                            stroke="#3b82f6"
                                            type="monotone"
                                        />
                                    </AreaChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </Card>
                </Col>
                <Col lg={8} xs={24}>
                    <Card title="活跃用户">
                        {(overview.activeUsers ?? []).length === 0 ? (
                            <Empty description="暂无活跃用户" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                        ) : (
                            <List
                                dataSource={overview.activeUsers}
                                renderItem={(user) => (
                                    <List.Item>
                                        <span>{user.name ?? "—"}</span>
                                        <Tag>{user.operations ?? 0} 次操作</Tag>
                                    </List.Item>
                                )}
                            />
                        )}
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]}>
                <Col lg={16} xs={24}>
                    <Card title="最近操作">
                        <Table
                            columns={[
                                {
                                    title: "时间",
                                    dataIndex: "timestamp",
                                    key: "timestamp",
                                    render: (value: string) => formatDateTime(value),
                                },
                                { title: "操作人", dataIndex: "operator", key: "operator" },
                                { title: "操作", dataIndex: "operation", key: "operation" },
                                {
                                    title: "资源",
                                    key: "resource",
                                    render: (_, record) => record.resourceName ?? record.resourceType ?? "—",
                                },
                                {
                                    title: "结果",
                                    dataIndex: "result",
                                    key: "result",
                                    render: (value: string) => <ResultTag result={value} />,
                                },
                            ]}
                            dataSource={overview.recentAuditLogs}
                            pagination={false}
                            rowKey={(record) => record.auditId ?? ""}
                            scroll={{ x: "max-content" }}
                            size="small"
                        />
                    </Card>
                </Col>
                <Col lg={8} xs={24}>
                    <Card title="角色分布">
                        {roleDistribution.length === 0 ? (
                            <Empty description="暂无角色数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                        ) : (
                            <Flex vertical gap={12}>
                                {roleDistribution.map((item) => (
                                    <div key={item.role ?? item.displayName}>
                                        <Flex justify="space-between">
                                            <span>{item.displayName ?? item.role ?? "—"}</span>
                                            <span>{item.users ?? 0}</span>
                                        </Flex>
                                        <Progress
                                            percent={Math.round(((item.users ?? 0) / maxRoleUsers) * 100)}
                                            showInfo={false}
                                            size="small"
                                        />
                                    </div>
                                ))}
                            </Flex>
                        )}
                    </Card>
                </Col>
            </Row>

            <Card title="站点信息">
                <Descriptions column={2} size="small">
                    <Descriptions.Item label="站点名称">{overview.globalInfo?.siteTitle ?? "—"}</Descriptions.Item>
                    <Descriptions.Item label="版本">{overview.globalInfo?.version ?? "—"}</Descriptions.Item>
                    <Descriptions.Item label="站点地址">{overview.globalInfo?.externalUrl ?? "—"}</Descriptions.Item>
                    <Descriptions.Item label="时区">{overview.globalInfo?.timezone ?? "—"}</Descriptions.Item>
                    <Descriptions.Item label="运行时长">
                        {formatDuration((overview.globalInfo?.uptimeSeconds ?? 0) * 1000)}
                    </Descriptions.Item>
                </Descriptions>
            </Card>
        </Flex>
    );
}
