"use client";

import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Alert, Empty, Select, Skeleton, Timeline, Typography } from "antd";

import { SectionCard } from "@/components/ui";

import { consoleErrorMessage } from "../../../../composables/_errors";

const ACTIVITY_DAY_OPTIONS = [
    { label: "最近 7 天", value: 7 },
    { label: "最近 14 天", value: 14 },
    { label: "最近 30 天", value: 30 },
    { label: "最近 90 天", value: 90 },
];

type UserDetailActivityProps = {
    name: string;
};

export function UserDetailActivity({ name }: UserDetailActivityProps) {
    const [days, setDays] = useState(30);
    const activityQuery = useQuery({
        queryKey: ["console", "users", "activity", name, days],
        queryFn: async () => (await apiClient.user.getUserActivity({ days, name })).data,
    });
    const activePoints = [...(activityQuery.data ?? [])].reverse().filter((point) => (point.count ?? 0) > 0);
    const total = (activityQuery.data ?? []).reduce((sum, point) => sum + (point.count ?? 0), 0);

    return (
        <SectionCard
            className="!h-auto"
            extra={
                <Select
                    aria-label="选择活动时间范围"
                    className="w-28"
                    onChange={setDays}
                    options={ACTIVITY_DAY_OPTIONS}
                    value={days}
                />
            }
            title="用户活动"
        >
            {activityQuery.isPending ? <Skeleton active paragraph={{ rows: 5 }} /> : null}
            {activityQuery.error ? (
                <Alert
                    action={<Typography.Link onClick={() => void activityQuery.refetch()}>重试</Typography.Link>}
                    message="用户活动加载失败"
                    description={consoleErrorMessage(activityQuery.error)}
                    showIcon
                    type="error"
                />
            ) : null}
            {activityQuery.isSuccess ? (
                <div>
                    <Typography.Text type="secondary">
                        最近 {days} 天共 {total} 次审计活动
                    </Typography.Text>
                    <div className="mt-4">
                        {activePoints.length > 0 ? (
                            <Timeline
                                items={activePoints.map((point) => ({
                                    color: "blue",
                                    content: (
                                        <div className="flex items-start justify-between gap-4">
                                            <div>
                                                <Typography.Text strong>{point.count ?? 0} 次操作</Typography.Text>
                                                <div>
                                                    <Typography.Text type="secondary">审计活动</Typography.Text>
                                                </div>
                                            </div>
                                            <Typography.Text className="whitespace-nowrap" type="secondary">
                                                {point.date}
                                            </Typography.Text>
                                        </div>
                                    ),
                                }))}
                            />
                        ) : (
                            <Empty description={`最近 ${days} 天暂无活动`} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                        )}
                    </div>
                </div>
            ) : null}
        </SectionCard>
    );
}
