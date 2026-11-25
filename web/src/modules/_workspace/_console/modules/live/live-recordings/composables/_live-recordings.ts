import type { LiveRecordingState, LiveRecordingView } from "../../../../composables/_live";

export type LiveRecordingStateMeta = {
    label: string;
    theme: "danger" | "default" | "primary" | "success" | "warning";
};

export const LIVE_RECORDING_PERMISSIONS = {
    manage: "liverecordings:manage",
    view: "liverecordings:view",
} as const;

export const LIVE_RECORDING_QUERY_ROOT = ["console", "live-recordings"] as const;
export const LIVE_RECORDING_LIST_QUERY_KEY = [...LIVE_RECORDING_QUERY_ROOT, "list"] as const;
export const LIVE_RECORDING_SUMMARY_QUERY_KEY = [...LIVE_RECORDING_QUERY_ROOT, "summary"] as const;

export const LIVE_RECORDING_PAGE_SIZE = 20;
export const LIVE_RECORDING_BATCH_LIMIT = 100;
export const LIVE_RECORDING_DOWNLOAD_TTL_SECONDS = 300;

const MILLISECONDS_PER_DAY = 86_400_000;

export const LIVE_RECORDING_RANGE_OPTIONS = [
    { label: "全部时间", value: 0 },
    { label: "最近 24 小时", value: 1 },
    { label: "最近 7 天", value: 7 },
    { label: "最近 30 天", value: 30 },
];

const LIVE_RECORDING_STATE_META: Record<LiveRecordingState, LiveRecordingStateMeta> = {
    CANCELED: { label: "已取消", theme: "default" },
    COMPLETED: { label: "已完成", theme: "success" },
    FAILED: { label: "失败", theme: "danger" },
    FINALIZING: { label: "收尾中", theme: "warning" },
    QUEUED: { label: "排队中", theme: "default" },
    RECORDING: { label: "录制中", theme: "primary" },
};

export const LIVE_RECORDING_STATE_FILTER_OPTIONS = Object.entries(LIVE_RECORDING_STATE_META).map(([value, meta]) => ({
    label: meta.label,
    value,
}));

const LIVE_RECORDING_ACTIVE_STATES: LiveRecordingState[] = ["QUEUED", "RECORDING", "FINALIZING"];
const LIVE_RECORDING_DOWNLOADABLE_STATES: LiveRecordingState[] = ["COMPLETED", "FAILED", "FINALIZING"];

export function liveRecordingStateMeta(state?: string | null): LiveRecordingStateMeta {
    const meta = state ? LIVE_RECORDING_STATE_META[state as LiveRecordingState] : undefined;

    return meta ?? { label: state ?? "未知", theme: "default" };
}

export function liveRecordingIsActive(state?: string | null): boolean {
    return Boolean(state && LIVE_RECORDING_ACTIVE_STATES.includes(state as LiveRecordingState));
}

export function liveRecordingIsDownloadable(state?: string | null): boolean {
    return Boolean(state && LIVE_RECORDING_DOWNLOADABLE_STATES.includes(state as LiveRecordingState));
}

export function liveRecordingDisplayName(recording: LiveRecordingView): string {
    return recording.roomTitle?.trim() || recording.name;
}

export function liveRecordingRangeStart(days: number): string | undefined {
    if (days <= 0) {
        return undefined;
    }

    return new Date(Date.now() - days * MILLISECONDS_PER_DAY).toISOString();
}

// 异步拿到下载地址后同步打开窗口，避免浏览器把弹出窗口当作广告拦截。
export function openDownloadUrl(url: string): boolean {
    const downloadWindow = window.open("", "_blank");

    if (!downloadWindow) {
        return false;
    }

    downloadWindow.opener = null;
    downloadWindow.location.replace(url);

    return true;
}
