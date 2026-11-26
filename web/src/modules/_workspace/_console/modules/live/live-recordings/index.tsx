"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MoreIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Dropdown, Empty, Input, Select, Skeleton, Tag, Tooltip } from "tdesign-react";
import type { DropdownOption, TableProps } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { type ConsoleOverviewMetric, ConsolePageOverview } from "../../../components/page-overview";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatBytes, formatCountOrDash, formatDateTime, formatElapsedTime } from "../../../composables/_format";
import {
    type LiveRecordingState,
    type LiveRecordingView,
    deleteLiveRecordings,
    getLiveRecordingSummary,
    queryLiveRecordings,
    stopLiveRecording,
} from "../../../composables/_live";
import { useConsoleSession } from "../../../composables/_session";
import { LiveRecordingDetailDialog } from "./components/live-recording-detail-dialog";
import { LiveRecordingDownloadDialog } from "./components/live-recording-download-dialog";
import { LiveRecordingRetryDialog } from "./components/live-recording-retry-dialog";
import {
    LIVE_RECORDING_BATCH_LIMIT,
    LIVE_RECORDING_LIST_QUERY_KEY,
    LIVE_RECORDING_PAGE_SIZE,
    LIVE_RECORDING_PERMISSIONS,
    LIVE_RECORDING_QUERY_ROOT,
    LIVE_RECORDING_RANGE_OPTIONS,
    LIVE_RECORDING_STATE_FILTER_OPTIONS,
    LIVE_RECORDING_SUMMARY_QUERY_KEY,
    liveRecordingDisplayName,
    liveRecordingIsActive,
    liveRecordingIsDownloadable,
    liveRecordingRangeStart,
    liveRecordingStateMeta,
} from "./composables/_live-recordings";

type LiveRecordingDialogState = {
    detail?: LiveRecordingView;
    download?: LiveRecordingView;
    retry?: LiveRecordingView;
};

export function LiveRecordingsView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        LIVE_RECORDING_PERMISSIONS.view,
    );
    const canManage = can(LIVE_RECORDING_PERMISSIONS.manage);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(LIVE_RECORDING_PAGE_SIZE);
    const [sourceName, setSourceName] = useState("");
    const [states, setStates] = useState<string[]>([]);
    const [rangeDays, setRangeDays] = useState(0);
    const [selectedKeys, setSelectedKeys] = useState<Array<string | number>>([]);
    const [dialogs, setDialogs] = useState<LiveRecordingDialogState>({});
    const trimmedSourceName = sourceName.trim();
    const recordingsQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        placeholderData: (previous) => previous,
        queryFn: async () =>
            queryLiveRecordings({
                from: liveRecordingRangeStart(rangeDays),
                page,
                size: pageSize,
                sourceName: trimmedSourceName || undefined,
                states: states.length > 0 ? (states as LiveRecordingState[]) : undefined,
            }),
        queryKey: [
            ...LIVE_RECORDING_LIST_QUERY_KEY,
            { from: rangeDays, page, pageSize, sourceName: trimmedSourceName, states },
        ],
    });
    const summaryQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        queryFn: async () => getLiveRecordingSummary(),
        queryKey: LIVE_RECORDING_SUMMARY_QUERY_KEY,
    });
    const refresh = () => {
        void queryClient.invalidateQueries({ queryKey: LIVE_RECORDING_QUERY_ROOT });
        void recordingsQuery.refetch();
    };
    const stopMutation = useMutation({
        mutationFn: async (name: string) => stopLiveRecording(name),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "已请求停止录制。" });
            refresh();
        },
    });
    const deleteMutation = useMutation({
        mutationFn: async (names: string[]) => deleteLiveRecordings(names),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (results) => {
            const accepted = results.filter((item) => item.result === "ACCEPTED").length;
            const rejected = results.filter((item) => item.result === "REJECTED");
            const firstReason = rejected.find((item) => item.message)?.message;

            if (rejected.length > 0) {
                prompt.warning({
                    content: `已删除 ${accepted} 条，${rejected.length} 条未能删除${firstReason ? `：${firstReason}` : "。"}`,
                });
            } else {
                prompt.success({ content: `已删除 ${accepted} 条录制记录。` });
            }

            setSelectedKeys([]);
            refresh();
        },
    });
    const recordings = recordingsQuery.data?.items ?? [];
    const total = recordingsQuery.data?.total ?? 0;
    const summary = summaryQuery.data;
    const metrics: ConsoleOverviewMetric[] = [
        { label: "录制总数", value: formatCountOrDash(summary?.total) },
        { label: "进行中", value: formatCountOrDash(summary?.active) },
        { label: "已完成", value: formatCountOrDash(summary?.completed) },
        { label: "失败", value: formatCountOrDash(summary?.failed) },
    ];
    const selectedNames = selectedKeys.map(String);
    const batchLimited = selectedNames.length > LIVE_RECORDING_BATCH_LIMIT;

    const handleRowAction = (recording: LiveRecordingView, value: string) => {
        if (value === "detail") {
            setDialogs({ detail: recording });
            return;
        }

        if (value === "stop") {
            confirmGlobalDialog({
                confirmText: "停止",
                content: `停止后当前分段会进入收尾合并，已录内容仍然保留。确认停止「${liveRecordingDisplayName(recording)}」？`,
                danger: true,
                onConfirm: () => stopMutation.mutate(recording.name),
                title: "停止录制",
            });
            return;
        }

        if (value === "download") {
            setDialogs({ download: recording });
            return;
        }

        if (value === "retry") {
            setDialogs({ retry: recording });
            return;
        }

        if (value === "delete") {
            handleDelete(
                [recording.name],
                `确认删除录制「${liveRecordingDisplayName(recording)}」？已上传的文件会一并清理。`,
            );
        }
    };

    const handleDelete = (names: string[], content: string) => {
        confirmGlobalDialog({
            confirmText: "删除",
            content,
            danger: true,
            onConfirm: () => deleteMutation.mutate(names),
            title: "删除录制",
        });
    };

    const columns: TableProps<LiveRecordingView>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="truncate font-medium">{liveRecordingDisplayName(row)}</span>
                    <Tooltip content={row.name} placement="top-left">
                        <span className="max-w-[220px] truncate font-mono text-xs">{row.name}</span>
                    </Tooltip>
                </div>
            ),
            colKey: "name",
            title: "录制",
            width: 260,
        },
        {
            cell: ({ row }) => (row.sourceName ? row.sourceName : <EmptyValue />),
            colKey: "sourceName",
            title: "直播源",
            width: 180,
        },
        {
            cell: ({ row }) => {
                const meta = liveRecordingStateMeta(row.state);

                return (
                    <div className="flex flex-wrap items-center gap-1">
                        <Tag theme={meta.theme} variant="light">
                            {meta.label}
                        </Tag>
                        {row.errorCode ? (
                            <Tooltip content={row.errorCode} placement="top-left">
                                <Tag theme="danger" variant="light">
                                    {row.errorCode}
                                </Tag>
                            </Tooltip>
                        ) : null}
                    </div>
                );
            },
            colKey: "state",
            title: "状态",
            width: 200,
        },
        { cell: ({ row }) => formatDateTime(row.startedAt), colKey: "startedAt", title: "开始时间", width: 180 },
        {
            cell: ({ row }) => formatElapsedTime(row.durationMillis),
            colKey: "durationMillis",
            title: "时长",
            width: 140,
        },
        { cell: ({ row }) => formatBytes(row.totalBytes), colKey: "totalBytes", title: "大小", width: 110 },
        {
            cell: ({ row }) => `${row.partCount ?? 0} / ${row.segmentCount ?? 0}`,
            colKey: "partCount",
            title: "分段 / 切片",
            width: 110,
        },
        {
            align: "right",
            cell: ({ row }) => {
                const actions: DropdownOption[] = [{ content: "查看详情", value: "detail" }];

                if (canManage && liveRecordingIsActive(row.state)) {
                    actions.push({ content: "停止录制", value: "stop" });
                }

                if (liveRecordingIsDownloadable(row.state)) {
                    actions.push({ content: "下载", value: "download" });
                }

                if (canManage && row.state === "FAILED") {
                    actions.push({ content: "重试", value: "retry" });
                }

                if (canManage && !liveRecordingIsActive(row.state)) {
                    actions.push(
                        { divider: true, value: "divider" },
                        { content: "删除", theme: "error", value: "delete" },
                    );
                }

                return (
                    <Dropdown
                        onClick={(option) => handleRowAction(row, String(option.value))}
                        options={actions}
                        placement="bottom-right"
                        trigger="click"
                    >
                        <Button
                            aria-label={`${liveRecordingDisplayName(row)} 操作`}
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
        return <Empty description="当前账号没有查看录制记录的权限。" type="fail" />;
    }

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <PageBreadcrumb current="直播录制" parents={["媒体监控"]} />
                <ConsolePageOverview
                    actions={
                        <Button
                            icon={<RefreshIcon />}
                            loading={recordingsQuery.isFetching}
                            onClick={refresh}
                            variant="outline"
                        >
                            刷新
                        </Button>
                    }
                    icon={<span>⏺</span>}
                    metrics={metrics}
                    title="直播录制"
                />
                <ConsoleTable<LiveRecordingView>
                    columns={columns}
                    data={recordings}
                    error={recordingsQuery.error ?? summaryQuery.error}
                    extra={
                        <div className="flex items-center justify-end gap-2">
                            <Dropdown
                                disabled={selectedNames.length === 0}
                                onClick={(option) => {
                                    if (option.value !== "delete") {
                                        return;
                                    }

                                    handleDelete(
                                        selectedNames.slice(0, LIVE_RECORDING_BATCH_LIMIT),
                                        `确认删除选中的 ${Math.min(selectedNames.length, LIVE_RECORDING_BATCH_LIMIT)} 条录制？进行中的录制会被拒绝删除。`,
                                    );
                                }}
                                options={[
                                    {
                                        content: batchLimited
                                            ? `批量删除（单次最多 ${LIVE_RECORDING_BATCH_LIMIT} 条）`
                                            : `批量删除${selectedNames.length > 0 ? `（${selectedNames.length}）` : ""}`,
                                        disabled: batchLimited,
                                        theme: "error",
                                        value: "delete",
                                    },
                                ]}
                                placement="bottom-right"
                                trigger="click"
                            >
                                <Button disabled={selectedNames.length === 0} icon={<MoreIcon />} variant="outline">
                                    批量操作
                                </Button>
                            </Dropdown>
                            <Button icon={<RefreshIcon />} onClick={refresh} size="small" variant="outline">
                                刷新
                            </Button>
                        </div>
                    }
                    loading={recordingsQuery.isPending}
                    onRetry={refresh}
                    pagination={{
                        current: page,
                        onChange: ({ current, pageSize: nextPageSize }) => {
                            if (nextPageSize !== pageSize) {
                                setPageSize(nextPageSize);
                                setPage(1);
                                return;
                            }

                            setPage(current);
                        },
                        pageSize,
                        showJumper: false,
                        total,
                        totalContent: `共 ${total} 条`,
                    }}
                    rowKey="name"
                    tableProps={{
                        onSelectChange: (keys) => setSelectedKeys(keys),
                        selectedRowKeys: selectedKeys,
                    }}
                    title="直播录制"
                    toolbar={
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                                <Input
                                    clearable
                                    onChange={(value) => {
                                        setSourceName(value);
                                        setPage(1);
                                    }}
                                    placeholder="按直播源名称筛选"
                                    style={{ width: 240 }}
                                    value={sourceName}
                                />
                                <Select
                                    clearable
                                    max={4}
                                    multiple
                                    onChange={(value) => {
                                        setStates(value as string[]);
                                        setPage(1);
                                    }}
                                    options={LIVE_RECORDING_STATE_FILTER_OPTIONS}
                                    placeholder="全部状态"
                                    style={{ width: 240 }}
                                    value={states}
                                />
                                <Select
                                    onChange={(value) => {
                                        setRangeDays(Number(value));
                                        setPage(1);
                                    }}
                                    options={LIVE_RECORDING_RANGE_OPTIONS}
                                    style={{ width: 160 }}
                                    value={rangeDays}
                                    valueType="value"
                                />
                            </div>
                            <span className="text-xs text-[var(--td-text-color-placeholder)]">
                                共 {total} 条，本页进行中{" "}
                                {recordings.filter((item) => liveRecordingIsActive(item.state)).length} 条
                            </span>
                        </div>
                    }
                />
            </div>
            <LiveRecordingDetailDialog onClose={() => setDialogs({})} recording={dialogs.detail} />
            <LiveRecordingDownloadDialog onClose={() => setDialogs({})} recording={dialogs.download} />
            <LiveRecordingRetryDialog
                key={dialogs.retry?.name ?? "none"}
                onClose={() => setDialogs({})}
                recording={dialogs.retry}
            />
        </>
    );
}
