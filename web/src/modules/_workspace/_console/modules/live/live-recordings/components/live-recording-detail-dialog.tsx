"use client";

import { Tag } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { formatBytes, formatDateTime, formatElapsedTime } from "../../../../composables/_format";
import type { LiveRecordingView } from "../../../../composables/_live";
import { livePlatformLabel } from "../../live-sources/composables/_live-sources";
import { liveRecordingDisplayName, liveRecordingStateMeta } from "../composables/_live-recordings";

type LiveRecordingDetailDialogProps = {
    onClose: () => void;
    recording?: LiveRecordingView;
};

export function LiveRecordingDetailDialog({ onClose, recording }: LiveRecordingDetailDialogProps) {
    if (!recording) {
        return null;
    }

    const stateMeta = liveRecordingStateMeta(recording.state);
    const items = [
        { label: "录制名称", value: <CopyableIdentifier value={recording.name} /> },
        { label: "直播间标题", value: liveRecordingDisplayName(recording) },
        {
            label: "直播源",
            value: recording.sourceName ? <CopyableIdentifier value={recording.sourceName} /> : <EmptyValue />,
        },
        { label: "主播", value: recording.anchorName ? recording.anchorName : <EmptyValue /> },
        { label: "平台", value: livePlatformLabel(recording.platform) },
        {
            label: "状态",
            value: (
                <Tag theme={stateMeta.theme} variant="light">
                    {stateMeta.label}
                </Tag>
            ),
        },
        { label: "错误码", value: recording.errorCode ? recording.errorCode : <EmptyValue /> },
        { label: "开始时间", value: formatDateTime(recording.startedAt) },
        { label: "结束时间", value: formatDateTime(recording.endedAt) },
        { label: "录制时长", value: formatElapsedTime(recording.durationMillis) },
        { label: "累计大小", value: formatBytes(recording.totalBytes) },
        { label: "本地大小", value: formatBytes(recording.localBytes) },
        { label: "分段数量", value: String(recording.partCount ?? 0) },
        { label: "切片数量", value: String(recording.segmentCount ?? 0) },
        { label: "存储策略", value: recording.storagePolicy ? recording.storagePolicy : <EmptyValue /> },
        { label: "封装格式", value: recording.format ? recording.format : <EmptyValue /> },
        { label: "自动录制", value: recording.auto ? "是" : "否" },
        { label: "最近写入", value: formatDateTime(recording.lastWriteAt) },
        { label: "最近数据包", value: formatDateTime(recording.lastPacketAt) },
        {
            label: "重试自",
            value: recording.retryOf ? <CopyableIdentifier value={recording.retryOf} /> : <EmptyValue />,
        },
    ];

    return (
        <GlobalDialog footer={false} onClose={onClose} open title="录制详情" width={760}>
            <DescriptionGrid columns={2} items={items} />
        </GlobalDialog>
    );
}
