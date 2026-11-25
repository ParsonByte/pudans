import { axiosInstance } from "@pudans-dev/api-client";

import type { ExtensionList, ExtensionMeta } from "./_extension";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

type LivePlatform = "DOUYIN";

type LiveRecordingFormat = "TS";

export type LiveSourceState = "IDLE" | "MONITORING" | "LIVE" | "RECORDING" | "OFFLINE" | "ERROR" | "DISABLED";

export type LiveRecordingState = "QUEUED" | "RECORDING" | "FINALIZING" | "COMPLETED" | "FAILED" | "CANCELED";

type LiveRecordingPartState = "MERGING" | "UPLOADING" | "UPLOADED" | "FAILED";

type LiveSourceSpec = {
    alias?: string;
    autoRecord?: boolean;
    checkIntervalSeconds?: number;
    enabled?: boolean;
    mergeIntervalSeconds?: number;
    platform?: LivePlatform;
    recordingFormat?: LiveRecordingFormat;
    roomId?: string;
    roomUrl?: string;
    segmentSeconds?: number;
    storagePolicy?: string;
};

export type LiveSourceStatus = {
    anchorAvatar?: string;
    anchorId?: string;
    anchorName?: string;
    cooldownUntil?: string;
    currentRecording?: string;
    lastLiveAt?: string;
    lastOfflineAt?: string;
    lastResolveCode?: string;
    lastResolveError?: string;
    lastResolvedAt?: string;
    lastStreamAt?: string;
    live?: boolean;
    liveStatus?: string;
    message?: string;
    nextCheckAt?: string;
    rateLimited?: boolean;
    resolveFailures?: number;
    roomTitle?: string;
    state?: LiveSourceState;
};

export type LiveSource = {
    apiVersion?: string;
    kind?: string;
    metadata: ExtensionMeta;
    spec: LiveSourceSpec;
    status?: LiveSourceStatus;
};

export type LiveSourceHealth = {
    anchorName?: string;
    cooldownUntil?: string;
    currentRecording?: string;
    lastResolveCode?: string;
    lastResolveError?: string;
    lastResolvedAt?: string;
    lastStreamAt?: string;
    live?: boolean;
    name?: string;
    nextCheckAt?: string;
    roomTitle?: string;
    state?: LiveSourceState;
};

export type LiveSourceSummary = {
    enabled: number;
    error: number;
    live: number;
    offline: number;
    rateLimited: number;
    recording: number;
    total: number;
};

type LiveRecordingPart = {
    durationMillis?: number;
    endedAt?: string;
    errorCode?: string;
    filename?: string;
    index?: number;
    message?: string;
    segmentCount?: number;
    size?: number;
    startedAt?: string;
    state?: LiveRecordingPartState;
    storageFile?: string;
};

type LiveRecordingSpec = {
    anchorName?: string;
    auto?: boolean;
    format?: LiveRecordingFormat;
    mergeIntervalSeconds?: number;
    platform?: LivePlatform;
    requestedBy?: string;
    retryOf?: string;
    roomId?: string;
    roomTitle?: string;
    segmentSeconds?: number;
    sourceName?: string;
    startedAt?: string;
    storagePolicy?: string;
};

type LiveRecordingStatus = {
    attempts?: number;
    cleanupAttempts?: number;
    durationMillis?: number;
    endedAt?: string;
    errorCode?: string;
    heartbeatAt?: string;
    lastPacketAt?: string;
    lastWriteAt?: string;
    localBytes?: number;
    message?: string;
    ownerInstance?: string;
    partCount?: number;
    parts?: LiveRecordingPart[];
    resolveAttempts?: number;
    segmentCount?: number;
    startedAt?: string;
    state?: LiveRecordingState;
    stopRequestedAt?: string;
    totalBytes?: number;
};

export type LiveRecording = {
    apiVersion?: string;
    kind?: string;
    metadata: ExtensionMeta;
    spec: LiveRecordingSpec;
    status?: LiveRecordingStatus;
};

export type LiveRecordingView = {
    anchorName?: string;
    auto?: boolean;
    durationMillis?: number;
    endedAt?: string;
    errorCode?: string;
    format?: LiveRecordingFormat;
    lastPacketAt?: string;
    lastWriteAt?: string;
    localBytes?: number;
    name: string;
    partCount?: number;
    platform?: LivePlatform;
    retryOf?: string;
    roomId?: string;
    roomTitle?: string;
    segmentCount?: number;
    sourceName?: string;
    startedAt?: string;
    state?: LiveRecordingState;
    storagePolicy?: string;
    totalBytes?: number;
};

export type LiveRecordingPage = {
    items?: LiveRecordingView[];
    page?: number;
    size?: number;
    total?: number;
};

export type LiveRecordingSummary = {
    active: number;
    canceled: number;
    completed: number;
    failed: number;
    localBytes: number;
    total: number;
    totalBytes: number;
};

export type LiveRecordingDownloadPart = {
    expiresAt?: string;
    filename?: string;
    index?: number;
    proxyRequired?: boolean;
    size?: number;
    url?: string;
};

export type LiveRecordingDownload = {
    parts?: LiveRecordingDownloadPart[];
    recording?: string;
};

export type LiveRecordingRetryMode = "UPLOAD" | "STREAM";

export type LiveRecordingRetry = {
    mode?: LiveRecordingRetryMode;
    recording?: string;
};

type LiveBatchDeleteResult = "ACCEPTED" | "REJECTED" | "NOT_FOUND";

export type LiveBatchDeleteItem = {
    code?: string;
    message?: string;
    name?: string;
    result?: LiveBatchDeleteResult;
};

export type LiveSourceCreateRequest = {
    alias?: string;
    autoRecord?: boolean;
    mergeIntervalSeconds?: number;
    roomUrl: string;
    segmentSeconds?: number;
    storagePolicy?: string;
};

// 直播源列表走扩展通用路由，服务端筛选由字段选择器完成。
export async function listLiveSources(params: { page?: number; size?: number }): Promise<ExtensionList<LiveSource>> {
    return (await axiosInstance.get<ExtensionList<LiveSource>>(`${API_ROOT}/livesources`, { params })).data;
}

export async function getLiveSourceSummary(): Promise<LiveSourceSummary> {
    return (await axiosInstance.get<LiveSourceSummary>(`${API_ROOT}/livesources/summary`)).data;
}

export async function getLiveSourceHealth(name: string): Promise<LiveSourceHealth> {
    return (await axiosInstance.get<LiveSourceHealth>(`${API_ROOT}/livesources/${encodeURIComponent(name)}/health`))
        .data;
}

// 预览解析不入库，用于创建前确认链接与房间信息。
export async function resolveLiveSourceDraft(payload: LiveSourceCreateRequest): Promise<LiveSourceHealth> {
    return (await axiosInstance.post<LiveSourceHealth>(`${API_ROOT}/livesources/resolve`, payload)).data;
}

export async function createLiveSourceFromUrl(payload: LiveSourceCreateRequest): Promise<LiveSource> {
    return (await axiosInstance.post<LiveSource>(`${API_ROOT}/livesources/from-url`, payload)).data;
}

export async function resolveLiveSource(name: string): Promise<LiveSourceHealth> {
    return (await axiosInstance.post<LiveSourceHealth>(`${API_ROOT}/livesources/${encodeURIComponent(name)}/resolve`))
        .data;
}

export async function recordLiveSource(name: string): Promise<LiveRecording> {
    return (await axiosInstance.post<LiveRecording>(`${API_ROOT}/livesources/${encodeURIComponent(name)}/record`)).data;
}

export async function deleteLiveSource(name: string): Promise<void> {
    await axiosInstance.delete(`${API_ROOT}/livesources/${encodeURIComponent(name)}`);
}

export async function queryLiveRecordings(params: {
    from?: string;
    page?: number;
    size?: number;
    sourceName?: string;
    states?: LiveRecordingState[];
    to?: string;
}): Promise<LiveRecordingPage> {
    return (
        await axiosInstance.get<LiveRecordingPage>(`${API_ROOT}/liverecordings/query`, {
            params: {
                from: params.from,
                page: params.page,
                size: params.size,
                sourceName: params.sourceName,
                states: params.states && params.states.length > 0 ? params.states.join(",") : undefined,
                to: params.to,
            },
        })
    ).data;
}

export async function getLiveRecordingSummary(): Promise<LiveRecordingSummary> {
    return (await axiosInstance.get<LiveRecordingSummary>(`${API_ROOT}/liverecordings/summary`)).data;
}

export async function stopLiveRecording(name: string): Promise<LiveRecordingView> {
    return (await axiosInstance.post<LiveRecordingView>(`${API_ROOT}/liverecordings/${encodeURIComponent(name)}/stop`))
        .data;
}

export async function createLiveRecordingDownload(
    name: string,
    options: { partIndex?: number; ttlSeconds?: number } = {},
): Promise<LiveRecordingDownload> {
    return (
        await axiosInstance.post<LiveRecordingDownload>(
            `${API_ROOT}/liverecordings/${encodeURIComponent(name)}/download-url`,
            options,
        )
    ).data;
}

export async function retryLiveRecording(name: string, mode?: LiveRecordingRetryMode): Promise<LiveRecordingRetry> {
    return (
        await axiosInstance.post<LiveRecordingRetry>(
            `${API_ROOT}/liverecordings/${encodeURIComponent(name)}/retry`,
            mode ? { mode } : {},
        )
    ).data;
}

export async function deleteLiveRecordings(names: string[]): Promise<LiveBatchDeleteItem[]> {
    return (
        await axiosInstance.delete<LiveBatchDeleteItem[]>(`${API_ROOT}/liverecordings/batch`, {
            data: { names },
        })
    ).data;
}
