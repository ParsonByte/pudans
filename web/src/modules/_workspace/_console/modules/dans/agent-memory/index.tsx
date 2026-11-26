"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { HistoryIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Select, Tabs, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import {
    type MemoryJob,
    type MemoryPoolItem,
    type MemoryVectorSpace,
    listMemoryJobs,
    listMemoryPool,
    listMemoryVectorSpaces,
} from "../composables/_dans";

const TABS = [
    { label: "记忆池", value: "pool" },
    { label: "提取任务", value: "jobs" },
    { label: "向量空间", value: "vectors" },
] as const;

const STATUS_OPTIONS = [
    { label: "候选待审核", value: "candidate" },
    { label: "已审核", value: "approved" },
    { label: "已发布", value: "published" },
    { label: "已拒绝", value: "rejected" },
    { label: "已替代", value: "superseded" },
] as const;

type MemoryTab = (typeof TABS)[number]["value"];

export function AgentMemoryView() {
    const [tab, setTab] = useState<MemoryTab>("pool");
    const [poolStatus, setPoolStatus] = useState("published");
    const poolQuery = useQuery({
        queryFn: () => listMemoryPool(poolStatus, 100),
        queryKey: ["dans", "agent", "memory-pool", poolStatus],
    });
    const jobsQuery = useQuery({
        queryFn: () => listMemoryJobs(100),
        queryKey: ["dans", "agent", "memory-jobs"],
    });
    const vectorsQuery = useQuery({
        queryFn: listMemoryVectorSpaces,
        queryKey: ["dans", "agent", "memory-vector-spaces"],
    });
    const pool = poolQuery.data ?? [];
    const jobs = jobsQuery.data ?? [];
    const vectors = vectorsQuery.data ?? [];
    const activeQuery = tab === "pool" ? poolQuery : tab === "jobs" ? jobsQuery : vectorsQuery;
    const refresh = () => {
        void poolQuery.refetch();
        void jobsQuery.refetch();
        void vectorsQuery.refetch();
    };
    const poolColumns: TableProps<MemoryPoolItem>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.title ?? <EmptyValue />}</span>
                    <span className="line-clamp-2 max-w-[360px] text-xs text-[var(--td-text-color-secondary)]">
                        {row.summary ?? row.generalizedContent ?? "—"}
                    </span>
                </div>
            ),
            colKey: "title",
            title: "记忆",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-wrap gap-1">
                    <Tag variant="light">{row.layer ?? "—"}</Tag>
                    <Tag variant="light">{row.memoryType ?? "—"}</Tag>
                </div>
            ),
            colKey: "layer",
            title: "层级 / 类型",
        },
        {
            cell: ({ row }) => `${row.sourceOwnerCount ?? 0} / ${row.sourceWorkspaceCount ?? 0}`,
            colKey: "sourceOwnerCount",
            title: "来源账号 / 工作区",
        },
        {
            cell: ({ row }) => (
                <Tag
                    theme={row.safetyClass === "general" ? "success" : row.safetyClass ? "danger" : "default"}
                    variant="light"
                >
                    {row.safetyClass ?? "未知"}
                </Tag>
            ),
            colKey: "safetyClass",
            title: "安全等级",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "published" ? "success" : "primary"} variant="light">
                    {row.status ?? "未知"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => formatDateTime(row.reviewedAt), colKey: "reviewedAt", title: "审核时间" },
        { cell: ({ row }) => formatDateTime(row.updatedAt), colKey: "updatedAt", title: "更新时间" },
    ];
    const jobColumns: TableProps<MemoryJob>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.jobType ?? <EmptyValue />}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                        {row.scopeType ?? "—"}
                    </span>
                </div>
            ),
            colKey: "jobType",
            title: "任务",
        },
        {
            cell: ({ row }) => `${row.processedItems ?? 0} / ${row.totalItems ?? 0}`,
            colKey: "processedItems",
            title: "已处理 / 总数",
        },
        {
            cell: ({ row }) => (
                <Tag
                    theme={row.status === "failed" ? "danger" : row.status === "succeeded" ? "success" : "primary"}
                    variant="light"
                >
                    {row.status ?? "未知"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => formatDateTime(row.createdAt), colKey: "createdAt", title: "创建时间" },
        { cell: ({ row }) => row.errorCode ?? <EmptyValue />, colKey: "errorCode", title: "错误码" },
    ];
    const vectorColumns: TableProps<MemoryVectorSpace>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="truncate font-mono text-xs">{row.tableName ?? row.id}</span>
                    <span className="text-xs text-[var(--td-text-color-secondary)]">{row.modelKey ?? "—"}</span>
                </div>
            ),
            colKey: "tableName",
            title: "向量空间",
        },
        {
            cell: ({ row }) => `${row.dimension ?? "—"} / ${row.metric ?? "—"}`,
            colKey: "dimension",
            title: "维度 / 度量",
        },
        { cell: ({ row }) => row.storageType ?? <EmptyValue />, colKey: "storageType", title: "存储类型" },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "ready" ? "success" : "primary"} variant="light">
                    {row.status ?? "未知"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => formatDateTime(row.updatedAt), colKey: "updatedAt", title: "更新时间" },
    ];

    return (
        <DansPage
            actions={
                <div className="flex flex-wrap items-center gap-2">
                    {tab === "pool" ? (
                        <Select
                            className="w-[160px]"
                            onChange={(value) => setPoolStatus(String(value))}
                            options={STATUS_OPTIONS.map((item) => ({ label: item.label, value: item.value }))}
                            value={poolStatus}
                        />
                    ) : null}
                    <Button icon={<RefreshIcon />} loading={activeQuery.isFetching} onClick={refresh} variant="outline">
                        刷新
                    </Button>
                </div>
            }
            description="记忆池和提取任务使用平台管理范围；向量空间用于观察真实索引状态。"
            icon={<HistoryIcon size={22} />}
            metrics={[
                { label: "当前状态记忆", value: formatCountOrDash(poolQuery.data?.length) },
                { label: "提取任务", value: formatCountOrDash(jobsQuery.data?.length) },
                { label: "向量空间", value: formatCountOrDash(vectorsQuery.data?.length) },
            ]}
            section="Agent"
            title="记忆与理解"
        >
            <Tabs
                list={TABS.map((item) => ({ label: item.label, value: item.value }))}
                onChange={(value) => setTab(String(value) as MemoryTab)}
                value={tab}
            />
            {tab === "pool" ? (
                <ConsoleTable
                    columns={poolColumns}
                    data={pool}
                    error={poolQuery.error}
                    loading={poolQuery.isPending}
                    onRetry={() => void poolQuery.refetch()}
                    rowKey="id"
                    title="记忆池"
                />
            ) : tab === "jobs" ? (
                <ConsoleTable
                    columns={jobColumns}
                    data={jobs}
                    error={jobsQuery.error}
                    loading={jobsQuery.isPending}
                    onRetry={() => void jobsQuery.refetch()}
                    rowKey="id"
                    title="提取任务"
                />
            ) : (
                <ConsoleTable
                    columns={vectorColumns}
                    data={vectors}
                    error={vectorsQuery.error}
                    loading={vectorsQuery.isPending}
                    onRetry={() => void vectorsQuery.refetch()}
                    rowKey="id"
                    title="向量空间"
                />
            )}
        </DansPage>
    );
}
