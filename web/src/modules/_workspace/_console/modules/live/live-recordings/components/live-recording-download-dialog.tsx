"use client";

import { useQuery } from "@tanstack/react-query";
import { DownloadIcon } from "tdesign-icons-react";
import { Button, Empty, Skeleton, Table, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { EmptyValue } from "../../../../components/empty-value";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatBytes, formatDateTime } from "../../../../composables/_format";
import {
    type LiveRecordingDownloadPart,
    type LiveRecordingView,
    createLiveRecordingDownload,
} from "../../../../composables/_live";
import {
    LIVE_RECORDING_DOWNLOAD_TTL_SECONDS,
    LIVE_RECORDING_QUERY_ROOT,
    liveRecordingDisplayName,
    openDownloadUrl,
} from "../composables/_live-recordings";

type LiveRecordingDownloadDialogProps = {
    onClose: () => void;
    recording?: LiveRecordingView;
};

const DOWNLOAD_BLOCKED_MESSAGE = "浏览器拦截了下载窗口，请允许弹出窗口后重试。";

export function LiveRecordingDownloadDialog({ onClose, recording }: LiveRecordingDownloadDialogProps) {
    const prompt = useMessagePrompt();
    const recordingName = recording?.name;
    const downloadQuery = useQuery({
        enabled: Boolean(recordingName),
        gcTime: 0,
        queryFn: async () =>
            createLiveRecordingDownload(recordingName ?? "", { ttlSeconds: LIVE_RECORDING_DOWNLOAD_TTL_SECONDS }),
        queryKey: [...LIVE_RECORDING_QUERY_ROOT, "download", recordingName],
        refetchOnWindowFocus: false,
        retry: false,
        staleTime: 0,
    });

    if (!recording) {
        return null;
    }

    const parts = downloadQuery.data?.parts ?? [];
    const columns: TableProps<LiveRecordingDownloadPart>["columns"] = [
        { cell: ({ row }) => row.index ?? 0, colKey: "index", title: "分段", width: 80 },
        {
            cell: ({ row }) => (
                <span className="block max-w-[280px] truncate font-mono text-xs">{row.filename ?? "—"}</span>
            ),
            colKey: "filename",
            title: "文件名",
        },
        { cell: ({ row }) => formatBytes(row.size), colKey: "size", title: "大小", width: 120 },
        { cell: ({ row }) => formatDateTime(row.expiresAt), colKey: "expiresAt", title: "过期时间", width: 180 },
        {
            cell: ({ row }) =>
                row.proxyRequired ? (
                    <Tag theme="warning" variant="light">
                        需经代理下载
                    </Tag>
                ) : (
                    <EmptyValue text="直链下载" />
                ),
            colKey: "proxyRequired",
            title: "下载方式",
            width: 140,
        },
        {
            align: "right",
            cell: ({ row }) => (
                <Button
                    disabled={!row.url}
                    icon={<DownloadIcon />}
                    onClick={() => {
                        if (row.url && !openDownloadUrl(row.url)) {
                            prompt.error({ content: DOWNLOAD_BLOCKED_MESSAGE });
                        }
                    }}
                    size="small"
                    variant="text"
                >
                    下载
                </Button>
            ),
            colKey: "operations",
            title: "操作",
            width: 100,
        },
    ];

    return (
        <GlobalDialog
            footer={false}
            onClose={onClose}
            open
            title={`下载录制 · ${liveRecordingDisplayName(recording)}`}
            width={860}
        >
            {downloadQuery.isPending ? <Skeleton animation="gradient" theme="paragraph" /> : null}
            {downloadQuery.error ? (
                <Empty
                    action={
                        <Button onClick={() => void downloadQuery.refetch()} variant="outline">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(downloadQuery.error)}
                    type="network-error"
                />
            ) : null}
            {downloadQuery.isSuccess ? (
                <div className="flex flex-col gap-3">
                    <Table<LiveRecordingDownloadPart>
                        columns={columns}
                        data={parts}
                        rowKey="index"
                        size="small"
                        empty={<Empty description="暂无可下载的分段" />}
                    />
                </div>
            ) : null}
        </GlobalDialog>
    );
}
