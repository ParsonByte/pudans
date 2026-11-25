"use client";

import { useQuery } from "@tanstack/react-query";
import { Tag } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { ObjectDetails } from "../../../../components/object-details";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatDateTime } from "../../../../composables/_format";
import { type LiveSource, type LiveSourceStatus, getLiveSourceHealth } from "../../../../composables/_live";
import { LIVE_SOURCE_HEALTH_QUERY_KEY, livePlatformLabel, liveSourceStateMeta } from "../composables/_live-sources";

type LiveSourceDetailDialogProps = {
    onClose: () => void;
    source?: LiveSource;
};

export function LiveSourceDetailDialog({ onClose, source }: LiveSourceDetailDialogProps) {
    const sourceName = source?.metadata.name;
    const healthQuery = useQuery({
        enabled: Boolean(sourceName),
        queryFn: async () => getLiveSourceHealth(sourceName ?? ""),
        queryKey: [...LIVE_SOURCE_HEALTH_QUERY_KEY, sourceName],
    });
    const status: LiveSourceStatus = { ...source?.status, ...healthQuery.data };
    const stateMeta = liveSourceStateMeta(status?.state);

    if (!source) {
        return null;
    }

    const items = [
        { label: "直播源名称", value: <CopyableIdentifier value={source.metadata.name} /> },
        { label: "别名", value: source.spec.alias ? source.spec.alias : <EmptyValue /> },
        { label: "直播平台", value: livePlatformLabel(source.spec.platform) },
        {
            label: "房间 ID",
            value: source.spec.roomId ? <CopyableIdentifier value={source.spec.roomId} /> : <EmptyValue />,
        },
        { label: "直播间标题", value: status?.roomTitle ? status.roomTitle : <EmptyValue /> },
        { label: "主播", value: status?.anchorName ? status.anchorName : <EmptyValue /> },
        {
            label: "监控状态",
            value: (
                <div className="flex flex-wrap items-center gap-1">
                    <Tag theme={stateMeta.theme} variant="light">
                        {stateMeta.label}
                    </Tag>
                    {status?.rateLimited ? (
                        <Tag theme="warning" variant="light">
                            已限流
                        </Tag>
                    ) : null}
                </div>
            ),
        },
        {
            label: "是否开播",
            value: status?.live ? (
                <Tag theme="success" variant="light">
                    直播中
                </Tag>
            ) : (
                <Tag variant="light">未开播</Tag>
            ),
        },
        {
            label: "当前录制",
            value: status?.currentRecording ? (
                <CopyableIdentifier value={status.currentRecording} />
            ) : (
                <EmptyValue text="未在录制" />
            ),
        },
        { label: "最近解析", value: formatDateTime(status?.lastResolvedAt) },
        { label: "最近开播", value: formatDateTime(status?.lastLiveAt) },
        { label: "最近下播", value: formatDateTime(status?.lastOfflineAt) },
        { label: "最近拉流", value: formatDateTime(status?.lastStreamAt) },
        { label: "下次检查", value: formatDateTime(status?.nextCheckAt) },
        { label: "冷却截止", value: formatDateTime(status?.cooldownUntil) },
        { label: "解析失败次数", value: String(status?.resolveFailures ?? 0) },
        { label: "最近解析结果", value: status?.lastResolveCode ? status.lastResolveCode : <EmptyValue /> },
        { label: "最近解析错误", value: status?.lastResolveError ? status.lastResolveError : <EmptyValue /> },
        { label: "存储策略", value: source.spec.storagePolicy ? source.spec.storagePolicy : <EmptyValue /> },
        { label: "分段时长", value: `${source.spec.segmentSeconds ?? 0} 秒` },
        { label: "合并间隔", value: `${source.spec.mergeIntervalSeconds ?? 0} 秒` },
        { label: "检查间隔", value: `${source.spec.checkIntervalSeconds ?? 0} 秒` },
        { label: "自动录制", value: source.spec.autoRecord ? "已开启" : "已关闭" },
        { label: "监控开关", value: source.spec.enabled ? "已启用" : "已停用" },
        {
            label: "直播间链接",
            value: source.spec.roomUrl ? <CopyableIdentifier value={source.spec.roomUrl} /> : <EmptyValue />,
        },
        { label: "状态说明", value: status?.message ? status.message : <EmptyValue /> },
    ];

    return (
        <GlobalDialog footer={false} onClose={onClose} open title="直播源详情" width={760}>
            {healthQuery.error ? (
                <p className="mb-3 text-xs" style={{ color: "var(--td-error-color)" }}>
                    {consoleErrorMessage(healthQuery.error)}
                </p>
            ) : null}
            <DescriptionGrid columns={2} items={items} />
            <div className="mt-4 flex flex-col gap-2">
                <span className="text-sm font-medium">原始对象</span>
                <ObjectDetails value={source} />
            </div>
        </GlobalDialog>
    );
}
