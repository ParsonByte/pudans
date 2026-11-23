"use client";

import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Empty, Select, Skeleton, Timeline, Typography } from "tdesign-react";

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
                    onChange={(value) => {
                        if (typeof value === "number") {
                            setDays(value);
                        }
                    }}
                    options={ACTIVITY_DAY_OPTIONS}
                    value={days}
                />
            }
            title="用户活动"
        >
            {activityQuery.isPending ? <Skeleton theme="paragraph" /> : null}
            {activityQuery.error ? (
                <Empty
                    action={
                        <Button onClick={() => void activityQuery.refetch()} size="small">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(activityQuery.error)}
                    title="用户活动加载失败"
                    type="fail"
                />
            ) : null}
            {activityQuery.isSuccess ? (
                <div>
                    <Typography.Text theme="secondary">
                        最近 {days} 天共 {total} 次审计活动
                    </Typography.Text>
                    <div className="mt-4">
                        {activePoints.length > 0 ? (
                            <Timeline mode="same" theme="dot">
                                {activePoints.map((point) => (
                                    <Timeline.Item dotColor="var(--td-brand-color)" key={point.date} label={point.date}>
                                        <Typography.Text strong>{point.count ?? 0} 次操作</Typography.Text>
                                    </Timeline.Item>
                                ))}
                            </Timeline>
                        ) : (
                            <Empty description={`最近 ${days} 天暂无活动`} />
                        )}
                    </div>
                </div>
            ) : null}
        </SectionCard>
    );
}
