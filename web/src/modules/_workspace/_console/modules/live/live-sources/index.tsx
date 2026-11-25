"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, MoreIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Dropdown, Empty, Input, Select, Skeleton, Tag, Tooltip } from "tdesign-react";
import type { DropdownOption, PaginationProps, TableProps } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { usePagination } from "@/hooks/table/use-pagination";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { type ConsoleOverviewMetric, ConsolePageOverview } from "../../../components/page-overview";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import {
    type LiveSource,
    deleteLiveSource,
    getLiveSourceSummary,
    listLiveSources,
    recordLiveSource,
    resolveLiveSource,
} from "../../../composables/_live";
import { useConsoleSession } from "../../../composables/_session";
import { LiveSourceCreateDialog } from "./components/live-source-create-dialog";
import { LiveSourceDetailDialog } from "./components/live-source-detail-dialog";
import {
    LIVE_SOURCE_LIST_QUERY_KEY,
    LIVE_SOURCE_LIST_SIZE,
    LIVE_SOURCE_PAGE_SIZE,
    LIVE_SOURCE_PERMISSIONS,
    LIVE_SOURCE_QUERY_ROOT,
    LIVE_SOURCE_STATE_FILTER_OPTIONS,
    LIVE_SOURCE_SUMMARY_QUERY_KEY,
    livePlatformLabel,
    liveSourceAnchorName,
    liveSourceDisplayName,
    liveSourceMatchesKeyword,
    liveSourceRecordingName,
    liveSourceRoomTitle,
    liveSourceStateMeta,
} from "./composables/_live-sources";

const ALL_STATE_VALUE = "ALL";
const STATE_FILTER_OPTIONS = [{ label: "全部状态", value: ALL_STATE_VALUE }, ...LIVE_SOURCE_STATE_FILTER_OPTIONS];
const MONO_TEXT_CLASS = "max-w-[220px] truncate font-mono text-xs";

export function LiveSourcesView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        LIVE_SOURCE_PERMISSIONS.view,
    );
    const canManage = can(LIVE_SOURCE_PERMISSIONS.manage);
    const [keyword, setKeyword] = useState("");
    const [stateFilter, setStateFilter] = useState(ALL_STATE_VALUE);
    const [createOpen, setCreateOpen] = useState(false);
    const [selected, setSelected] = useState<LiveSource>();
    const sourcesQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        queryFn: async () => listLiveSources({ page: 1, size: LIVE_SOURCE_LIST_SIZE }),
        queryKey: LIVE_SOURCE_LIST_QUERY_KEY,
    });
    const summaryQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        queryFn: async () => getLiveSourceSummary(),
        queryKey: LIVE_SOURCE_SUMMARY_QUERY_KEY,
    });
    const refresh = () => {
        void queryClient.invalidateQueries({ queryKey: LIVE_SOURCE_QUERY_ROOT });
    };
    const resolveSource = useMutation({
        mutationFn: async (name: string) => resolveLiveSource(name),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "已重新解析直播间信息。" });
            refresh();
        },
    });
    const recordSource = useMutation({
        mutationFn: async (name: string) => recordLiveSource(name),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "已提交录制请求。" });
            refresh();
        },
    });
    const removeSource = useMutation({
        mutationFn: async (name: string) => deleteLiveSource(name),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "直播源已删除。" });
            refresh();
        },
    });
    const filteredSources = (sourcesQuery.data?.items ?? []).filter((source) => {
        if (stateFilter !== ALL_STATE_VALUE && source.status?.state !== stateFilter) {
            return false;
        }

        return liveSourceMatchesKeyword(source, keyword);
    });
    const pagination = usePagination({ initialPageSize: LIVE_SOURCE_PAGE_SIZE, total: filteredSources.length });
    const pagedSources = filteredSources.slice(
        (pagination.page - 1) * pagination.pageSize,
        pagination.page * pagination.pageSize,
    );
    const summary = summaryQuery.data;
    const metrics: ConsoleOverviewMetric[] = [
        { label: "直播源总数", value: formatCountOrDash(summary?.total) },
        { label: "直播中", value: formatCountOrDash(summary?.live) },
        { label: "录制中", value: formatCountOrDash(summary?.recording) },
        { label: "异常", value: formatCountOrDash(summary?.error) },
    ];

    const handleSourceAction = (source: LiveSource, value: string) => {
        if (value === "detail") {
            setSelected(source);
            return;
        }

        if (value === "resolve") {
            resolveSource.mutate(source.metadata.name);
            return;
        }

        if (value === "record") {
            recordSource.mutate(source.metadata.name);
            return;
        }

        if (value === "delete") {
            confirmGlobalDialog({
                confirmText: "删除",
                content: `删除后该直播源将停止监控，已产生的录制不会被删除。确认删除「${liveSourceDisplayName(source)}」？`,
                danger: true,
                onConfirm: () => removeSource.mutate(source.metadata.name),
                title: "删除直播源",
            });
        }
    };

    const columns: TableProps<LiveSource>["columns"] = [
        {
            cell: ({ row }) => {
                const recordingName = liveSourceRecordingName(row);

                return (
                    <div className="flex min-w-0 flex-col gap-1">
                        <span className="truncate font-medium">{liveSourceDisplayName(row)}</span>
                        <Tooltip content={row.metadata.name} placement="top-left">
                            <span className={MONO_TEXT_CLASS}>{row.metadata.name}</span>
                        </Tooltip>
                        {recordingName ? (
                            <Tag theme="primary" variant="light">
                                录制中
                            </Tag>
                        ) : null}
                    </div>
                );
            },
            colKey: "metadata.name",
            title: "直播源",
            width: 260,
        },
        {
            cell: ({ row }) => livePlatformLabel(row.spec.platform),
            colKey: "spec.platform",
            title: "平台",
            width: 90,
        },
        {
            cell: ({ row }) => {
                const roomTitle = liveSourceRoomTitle(row);

                return (
                    <div className="flex min-w-0 flex-col gap-1">
                        <span className="truncate">{roomTitle ?? <EmptyValue />}</span>
                        <span className="font-mono text-xs">{row.spec.roomId ?? "—"}</span>
                    </div>
                );
            },
            colKey: "spec.roomId",
            ellipsis: false,
            title: "直播间",
        },
        {
            cell: ({ row }) => liveSourceAnchorName(row) ?? <EmptyValue />,
            colKey: "status.anchorName",
            title: "主播",
            width: 140,
        },
        {
            cell: ({ row }) => {
                const meta = liveSourceStateMeta(row.status?.state);
                const message = row.status?.message;

                return (
                    <div className="flex flex-wrap items-center gap-1">
                        <Tag theme={meta.theme} variant="light">
                            {meta.label}
                        </Tag>
                        {row.status?.rateLimited ? (
                            <Tag theme="warning" variant="light">
                                限流
                            </Tag>
                        ) : null}
                        {message ? (
                            <Tooltip content={message} placement="top-left">
                                <span className="max-w-[160px] truncate text-xs">{message}</span>
                            </Tooltip>
                        ) : null}
                    </div>
                );
            },
            colKey: "status.state",
            title: "状态",
            width: 200,
        },
        {
            cell: ({ row }) => {
                const recordingName = liveSourceRecordingName(row);

                if (!recordingName) {
                    return <EmptyValue text="未在录制" />;
                }

                return (
                    <Tooltip content={recordingName} placement="top-left">
                        <span className={MONO_TEXT_CLASS}>{recordingName}</span>
                    </Tooltip>
                );
            },
            colKey: "status.currentRecording",
            title: "当前录制",
            width: 200,
        },
        {
            cell: ({ row }) => formatDateTime(row.status?.lastLiveAt),
            colKey: "status.lastLiveAt",
            title: "最近开播",
            width: 180,
        },
        {
            align: "right",
            cell: ({ row }) => {
                const actions: DropdownOption[] = [{ content: "查看详情", value: "detail" }];

                if (canManage) {
                    actions.push(
                        { content: "立即解析", value: "resolve" },
                        { content: "立即录制", value: "record" },
                        { divider: true, value: "divider" },
                        { content: "删除直播源", theme: "error", value: "delete" },
                    );
                }

                return (
                    <Dropdown
                        onClick={(option) => handleSourceAction(row, String(option.value))}
                        options={actions}
                        placement="bottom-right"
                        trigger="click"
                    >
                        <Button
                            aria-label={`${liveSourceDisplayName(row)} 操作`}
                            icon={<MoreIcon />}
                            shape="square"
                            size="small"
                            variant="text"
                        />
                    </Dropdown>
                );
            },
            colKey: "operations",
            fixed: "right",
            title: "操作",
            width: 100,
        },
    ];

    if (sessionQuery.isPending) {
        return <Skeleton animation="gradient" theme="paragraph" />;
    }

    if (sessionQuery.error) {
        return (
            <Empty
                action={
                    <Button onClick={() => void sessionQuery.refetch()} variant="outline">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(sessionQuery.error)}
                type="network-error"
            />
        );
    }

    if (!canView) {
        return <Empty description="当前账号没有查看直播源的权限。" type="fail" />;
    }

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <PageBreadcrumb current="直播源" parents={["直播监控"]} />
                <ConsolePageOverview
                    actions={
                        <>
                            <Button
                                icon={<RefreshIcon />}
                                loading={sourcesQuery.isFetching}
                                onClick={refresh}
                                variant="outline"
                            >
                                刷新
                            </Button>
                            {canManage ? (
                                <Button icon={<AddIcon />} onClick={() => setCreateOpen(true)} theme="primary">
                                    新建直播源
                                </Button>
                            ) : null}
                        </>
                    }
                    icon={<span>📡</span>}
                    metrics={metrics}
                    title="直播源"
                />
                <ConsoleTable<LiveSource>
                    columns={columns}
                    data={pagedSources}
                    error={sourcesQuery.error ?? summaryQuery.error}
                    extra={
                        <Button icon={<RefreshIcon />} onClick={refresh} size="small" variant="outline">
                            刷新
                        </Button>
                    }
                    loading={sourcesQuery.isPending}
                    onRetry={refresh}
                    pagination={liveSourcesPaginationConfig(pagination, filteredSources.length)}
                    rowKey="metadata.name"
                    title="直播源"
                    toolbar={
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                                <Input
                                    clearable
                                    onChange={(value) => {
                                        setKeyword(value);
                                        pagination.setPage(1);
                                    }}
                                    placeholder="搜索名称、别名、房间或主播"
                                    style={{ width: 280 }}
                                    value={keyword}
                                />
                                <Select
                                    clearable={false}
                                    onChange={(value) => {
                                        setStateFilter(String(value));
                                        pagination.setPage(1);
                                    }}
                                    options={STATE_FILTER_OPTIONS}
                                    style={{ width: 160 }}
                                    value={stateFilter}
                                />
                            </div>
                            <span className="text-xs text-[var(--td-text-color-placeholder)]">
                                共 {filteredSources.length} 条
                            </span>
                        </div>
                    }
                />
            </div>
            <LiveSourceCreateDialog onClose={() => setCreateOpen(false)} open={createOpen} />
            <LiveSourceDetailDialog onClose={() => setSelected(undefined)} source={selected} />
        </>
    );
}

function liveSourcesPaginationConfig(pagination: ReturnType<typeof usePagination>, total: number): PaginationProps {
    return {
        current: pagination.page,
        onChange: ({ current, pageSize }) => {
            if (pageSize !== pagination.pageSize) {
                pagination.setPageSize(pageSize);
                return;
            }

            pagination.setPage(current);
        },
        pageSize: pagination.pageSize,
        showJumper: false,
        total,
        totalContent: `共 ${total} 条`,
    };
}
