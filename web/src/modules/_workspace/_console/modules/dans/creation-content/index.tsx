"use client";

import { useState } from "react";

import { useMutation, useQuery } from "@tanstack/react-query";
import { FileIcon, MoreIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Dropdown, Tabs, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import {
    type Activity,
    type ContentKind,
    type ContentReviewAction,
    type Template,
    type Work,
    changeContentStatus,
    listActivities,
    listExploreWorks,
    listTemplates,
} from "../composables/_dans";

const TABS = [
    { label: "作品", value: "works" },
    { label: "模板", value: "templates" },
    { label: "活动", value: "activities" },
] as const;

const CONTENT_STATUS_LABELS: Record<string, string> = {
    approved: "审核通过",
    draft: "草稿",
    published: "已上架",
    rejected: "审核拒绝",
};

const CONTENT_VISIBILITY_LABELS: Record<string, string> = {
    private: "私有",
    public: "公开",
    workspace: "工作区",
};

type ContentTab = (typeof TABS)[number]["value"];

function contentActions(status?: string): { content: string; value: ContentReviewAction }[] {
    if (status === "published") {
        return [{ content: "下架", value: "unpublish" }];
    }
    if (status === "approved") {
        return [
            { content: "上架", value: "publish" },
            { content: "审核拒绝", value: "reject" },
        ];
    }
    if (status === "rejected") {
        return [{ content: "审核通过", value: "approve" }];
    }
    return [
        { content: "审核通过", value: "approve" },
        { content: "审核拒绝", value: "reject" },
    ];
}

export function CreationContentView() {
    const [tab, setTab] = useState<ContentTab>("works");
    const prompt = useMessagePrompt();
    const worksQuery = useQuery({
        queryFn: () => listExploreWorks(100),
        queryKey: ["dans", "creation", "explore-feed"],
    });
    const templatesQuery = useQuery({
        queryFn: () => listTemplates(100),
        queryKey: ["dans", "creation", "template-feed"],
    });
    const activitiesQuery = useQuery({
        queryFn: () => listActivities(100),
        queryKey: ["dans", "creation", "activity-feed"],
    });
    const works = worksQuery.data?.items ?? [];
    const templates = templatesQuery.data?.items ?? [];
    const activities = activitiesQuery.data?.items ?? [];
    const activeQuery = tab === "works" ? worksQuery : tab === "templates" ? templatesQuery : activitiesQuery;
    const refresh = () => {
        void worksQuery.refetch();
        void templatesQuery.refetch();
        void activitiesQuery.refetch();
    };
    const contentAction = useMutation({
        mutationFn: (input: { action: ContentReviewAction; id: string; kind: ContentKind }) =>
            changeContentStatus(input.kind, input.id, input.action),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "内容状态已更新。" });
            refresh();
        },
    });
    const actionPending = (id: string) => contentAction.isPending && contentAction.variables?.id === id;
    const workColumns: TableProps<Work>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.title ?? "未命名作品"}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">{row.id}</span>
                </div>
            ),
            colKey: "title",
            title: "作品",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-wrap gap-1">
                    <Tag theme={row.status === "published" ? "success" : "default"} variant="light">
                        {CONTENT_STATUS_LABELS[row.status ?? ""] ?? row.status ?? "未知"}
                    </Tag>
                    <Tag variant="light">
                        {CONTENT_VISIBILITY_LABELS[row.visibility ?? ""] ?? row.visibility ?? "—"}
                    </Tag>
                </div>
            ),
            colKey: "status",
            title: "状态 / 可见性",
        },
        {
            cell: ({ row }) => `${row.viewCount ?? 0} / ${row.likeCount ?? 0} / ${row.favoriteCount ?? 0}`,
            colKey: "viewCount",
            title: "浏览 / 点赞 / 收藏",
        },
        { cell: ({ row }) => row.ownerId ?? <EmptyValue />, colKey: "ownerId", title: "创作者" },
        { cell: ({ row }) => formatDateTime(row.publishedAt), colKey: "publishedAt", title: "发布时间" },
        {
            cell: ({ row }) => (
                <ContentActions
                    action={(action) => contentAction.mutate({ action, id: row.id, kind: "works" })}
                    disabled={actionPending(row.id)}
                    status={row.status}
                />
            ),
            colKey: "actions",
            title: "操作",
            width: 72,
        },
    ];
    const templateColumns: TableProps<Template>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.title ?? row.name ?? "未命名模板"}</span>
                    <span className="line-clamp-2 text-xs text-[var(--td-text-color-secondary)]">
                        {row.description ?? row.id}
                    </span>
                </div>
            ),
            colKey: "title",
            title: "模板",
        },
        { cell: ({ row }) => row.mediaType ?? <EmptyValue />, colKey: "mediaType", title: "媒体类型" },
        {
            cell: ({ row }) => (
                <div className="flex flex-wrap gap-1">
                    <Tag theme={row.status === "published" ? "success" : "default"} variant="light">
                        {CONTENT_STATUS_LABELS[row.status ?? ""] ?? row.status ?? "未知"}
                    </Tag>
                    <Tag variant="light">
                        {CONTENT_VISIBILITY_LABELS[row.visibility ?? ""] ?? row.visibility ?? "—"}
                    </Tag>
                </div>
            ),
            colKey: "status",
            title: "状态 / 可见性",
        },
        { cell: ({ row }) => row.ownerId ?? <EmptyValue />, colKey: "ownerId", title: "创作者" },
        { cell: ({ row }) => formatDateTime(row.publishedAt), colKey: "publishedAt", title: "发布时间" },
        {
            cell: ({ row }) => (
                <ContentActions
                    action={(action) => contentAction.mutate({ action, id: row.id, kind: "templates" })}
                    disabled={actionPending(row.id)}
                    status={row.status}
                />
            ),
            colKey: "actions",
            title: "操作",
            width: 72,
        },
    ];
    const activityColumns: TableProps<Activity>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.title ?? "未命名活动"}</span>
                    <span className="line-clamp-2 text-xs text-[var(--td-text-color-secondary)]">
                        {row.description ?? row.id}
                    </span>
                </div>
            ),
            colKey: "title",
            title: "活动",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-wrap gap-1">
                    <Tag theme={row.status === "published" ? "success" : "default"} variant="light">
                        {CONTENT_STATUS_LABELS[row.status ?? ""] ?? row.status ?? "未知"}
                    </Tag>
                    <Tag variant="light">
                        {CONTENT_VISIBILITY_LABELS[row.visibility ?? ""] ?? row.visibility ?? "—"}
                    </Tag>
                </div>
            ),
            colKey: "status",
            title: "状态 / 可见性",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-col gap-1 text-xs">
                    <span>起 {formatDateTime(row.startsAt)}</span>
                    <span>止 {formatDateTime(row.endsAt)}</span>
                </div>
            ),
            colKey: "startsAt",
            title: "活动周期",
        },
        { cell: ({ row }) => row.ownerId ?? <EmptyValue />, colKey: "ownerId", title: "创作者" },
        { cell: ({ row }) => formatDateTime(row.createdAt), colKey: "createdAt", title: "创建时间" },
        {
            cell: ({ row }) => (
                <ContentActions
                    action={(action) => contentAction.mutate({ action, id: row.id, kind: "activities" })}
                    disabled={actionPending(row.id)}
                    status={row.status}
                />
            ),
            colKey: "actions",
            title: "操作",
            width: 72,
        },
    ];

    return (
        <DansPage
            actions={
                <Button icon={<RefreshIcon />} loading={activeQuery.isFetching} onClick={refresh} variant="outline">
                    刷新
                </Button>
            }
            description="内容运营展示平台作品、模板和创作活动，并支持审核与上下架。"
            icon={<FileIcon size={22} />}
            metrics={[
                { label: "作品总数", value: formatCountOrDash(worksQuery.data?.items?.length) },
                { label: "模板总数", value: formatCountOrDash(templatesQuery.data?.items?.length) },
                { label: "活动总数", value: formatCountOrDash(activitiesQuery.data?.items?.length) },
            ]}
            section="创作"
            title="内容运营"
        >
            <Tabs
                list={TABS.map((item) => ({ label: item.label, value: item.value }))}
                onChange={(value) => setTab(String(value) as ContentTab)}
                value={tab}
            />
            {tab === "works" ? (
                <ConsoleTable
                    columns={workColumns}
                    data={works}
                    error={worksQuery.error}
                    loading={worksQuery.isPending}
                    onRetry={() => void worksQuery.refetch()}
                    rowKey="id"
                    title="作品列表"
                />
            ) : tab === "templates" ? (
                <ConsoleTable
                    columns={templateColumns}
                    data={templates}
                    error={templatesQuery.error}
                    loading={templatesQuery.isPending}
                    onRetry={() => void templatesQuery.refetch()}
                    rowKey="id"
                    title="模板列表"
                />
            ) : (
                <ConsoleTable
                    columns={activityColumns}
                    data={activities}
                    error={activitiesQuery.error}
                    loading={activitiesQuery.isPending}
                    onRetry={() => void activitiesQuery.refetch()}
                    rowKey="id"
                    title="活动列表"
                />
            )}
        </DansPage>
    );
}

function ContentActions({
    action,
    disabled,
    status,
}: {
    action: (action: ContentReviewAction) => void;
    disabled: boolean;
    status?: string;
}) {
    const actions = contentActions(status);
    if (actions.length === 0) {
        return <EmptyValue />;
    }
    return (
        <Dropdown
            disabled={disabled}
            onClick={(item) => action(item.value as ContentReviewAction)}
            options={actions}
            trigger="click"
        >
            <Button aria-label="内容操作" icon={<MoreIcon />} shape="square" variant="text" />
        </Dropdown>
    );
}
