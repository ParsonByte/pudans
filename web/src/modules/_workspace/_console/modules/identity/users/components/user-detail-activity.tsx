"use client";

import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, Empty, Select, Skeleton } from "tdesign-react";

import { TrendChart } from "../../../../components/trend-chart";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount, formatDateTick } from "../../../../composables/_format";

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
    const data = [...(activityQuery.data ?? [])]
        .sort((left, right) => (left.date ?? "").localeCompare(right.date ?? ""))
        .map((point) => ({
            count: point.count ?? 0,
            date: point.date,
            label: formatDateTick(point.date),
        }));
    const total = (activityQuery.data ?? []).reduce((sum, point) => sum + (point.count ?? 0), 0);

    return (
        <Card
            actions={
                <Select
                    aria-label="选择活动时间范围"
                    className="w-28"
                    onChange={(value) => {
                        if (typeof value === "number") {
                            setDays(value);
                        }
                    }}
                    options={ACTIVITY_DAY_OPTIONS}
                    size="small"
                    value={days}
                />
            }
            bordered={false}
            shadow={false}
            subtitle="（次）"
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
                <div className="min-w-0">
                    <div className="mb-4 text-xs text-[var(--td-text-color-secondary)]">
                        最近 {days} 天共 <span className="whitespace-nowrap">{formatCount(total)} 次</span> 审计活动
                    </div>
                    <TrendChart
                        data={data}
                        emptyText={`最近 ${days} 天暂无活动`}
                        height={360}
                        series={[{ color: "var(--td-brand-color)", key: "count", label: "活跃次数", type: "line" }]}
                    />
                </div>
            ) : null}
        </Card>
    );
}
