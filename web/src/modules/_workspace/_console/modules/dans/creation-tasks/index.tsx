"use client";

import { useQuery } from "@tanstack/react-query";
import { RefreshIcon, VideoIcon } from "tdesign-icons-react";
import { Button, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import { type GenerationTask, listGenerationTasks } from "../composables/_dans";

const FAILED_STATUSES = new Set(["failed", "cancelled"]);
const SUCCEEDED_STATUSES = new Set(["succeeded"]);

export function CreationTasksView() {
    const tasksQuery = useQuery({
        queryFn: () => listGenerationTasks(100),
        queryKey: ["dans", "creation", "generation-tasks"],
    });
    const tasks = tasksQuery.data?.items ?? [];
    const runningCount = tasks.filter(
        (task) => !FAILED_STATUSES.has(task.status ?? "") && !SUCCEEDED_STATUSES.has(task.status ?? ""),
    ).length;
    const failedCount = tasks.filter((task) => FAILED_STATUSES.has(task.status ?? "")).length;
    const succeededCount = tasks.filter((task) => SUCCEEDED_STATUSES.has(task.status ?? "")).length;
    const columns: TableProps<GenerationTask>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="line-clamp-2 max-w-[360px]">{row.prompt ?? <EmptyValue />}</span>
                    <Tooltip content={row.id} placement="top-left">
                        <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                            {row.id}
                        </span>
                    </Tooltip>
                </div>
            ),
            colKey: "prompt",
            title: "任务",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-wrap gap-1">
                    <Tag variant="light">{row.mediaType ?? "—"}</Tag>
                    <Tag variant="light">{row.operation ?? "—"}</Tag>
                </div>
            ),
            colKey: "mediaType",
            title: "媒体 / 操作",
        },
        { cell: ({ row }) => row.modelId ?? <EmptyValue />, colKey: "modelId", title: "模型标识" },
        {
            cell: ({ row }) => (
                <Tag
                    theme={
                        FAILED_STATUSES.has(row.status ?? "")
                            ? "danger"
                            : row.status === "succeeded"
                              ? "success"
                              : "primary"
                    }
                    variant="light"
                >
                    {row.status ?? "未知"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => `${row.resultAssetIds?.length ?? 0} 个`, colKey: "resultAssetIds", title: "结果资产" },
        { cell: ({ row }) => formatDateTime(row.startedAt), colKey: "startedAt", title: "开始时间" },
        { cell: ({ row }) => formatDateTime(row.finishedAt), colKey: "finishedAt", title: "结束时间" },
        {
            cell: ({ row }) =>
                row.errorCode ? (
                    <Tooltip content={row.errorMessage ?? row.errorCode} placement="top-left">
                        <span className="max-w-[180px] truncate font-mono text-xs">{row.errorCode}</span>
                    </Tooltip>
                ) : (
                    <EmptyValue />
                ),
            colKey: "errorCode",
            title: "异常",
        },
    ];

    return (
        <DansPage
            actions={
                <Button
                    icon={<RefreshIcon />}
                    loading={tasksQuery.isFetching}
                    onClick={() => void tasksQuery.refetch()}
                    variant="outline"
                >
                    刷新
                </Button>
            }
            description="生成任务展示平台范围的媒体类型、操作、模型、执行状态、错误和结果资产数量。"
            icon={<VideoIcon size={22} />}
            metrics={[
                { label: "任务总数", value: formatCountOrDash(tasksQuery.data?.items?.length) },
                { label: "处理中", value: formatCountOrDash(runningCount) },
                { label: "成功", value: formatCountOrDash(succeededCount) },
                { label: "失败或取消", value: formatCountOrDash(failedCount) },
            ]}
            section="创作"
            title="生成任务"
        >
            <ConsoleTable
                columns={columns}
                data={tasks}
                error={tasksQuery.error}
                loading={tasksQuery.isPending}
                onRetry={() => void tasksQuery.refetch()}
                rowKey="id"
                title="任务列表"
            />
        </DansPage>
    );
}
