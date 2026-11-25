import type { LiveSource, LiveSourceState } from "../../../../composables/_live";

export type LiveSourceStateMeta = {
    label: string;
    theme: "danger" | "default" | "primary" | "success" | "warning";
};

export const LIVE_SOURCE_PERMISSIONS = {
    manage: "livesources:manage",
    view: "livesources:view",
} as const;

export const LIVE_SOURCE_QUERY_ROOT = ["console", "live-sources"] as const;
export const LIVE_SOURCE_LIST_QUERY_KEY = [...LIVE_SOURCE_QUERY_ROOT, "list"] as const;
export const LIVE_SOURCE_SUMMARY_QUERY_KEY = [...LIVE_SOURCE_QUERY_ROOT, "summary"] as const;
export const LIVE_SOURCE_HEALTH_QUERY_KEY = [...LIVE_SOURCE_QUERY_ROOT, "health"] as const;

export const LIVE_SOURCE_LIST_SIZE = 1000;
export const LIVE_SOURCE_PAGE_SIZE = 20;
export const LIVE_SOURCE_DEFAULT_SEGMENT_SECONDS = 30;
export const LIVE_SOURCE_DEFAULT_MERGE_SECONDS = 3600;

export const LIVE_SOURCE_SEGMENT_OPTIONS = [
    { label: "10 秒", value: 10 },
    { label: "20 秒", value: 20 },
    { label: "30 秒", value: 30 },
    { label: "1 分钟", value: 60 },
    { label: "2 分钟", value: 120 },
    { label: "5 分钟", value: 300 },
];

export const LIVE_SOURCE_MERGE_OPTIONS = [
    { label: "30 分钟", value: 1800 },
    { label: "1 小时", value: 3600 },
    { label: "2 小时", value: 7200 },
    { label: "6 小时", value: 21600 },
];

const LIVE_PLATFORM_LABELS: Record<string, string> = {
    DOUYIN: "抖音",
};

const LIVE_SOURCE_STATE_META: Record<LiveSourceState, LiveSourceStateMeta> = {
    DISABLED: { label: "已停用", theme: "default" },
    ERROR: { label: "异常", theme: "danger" },
    IDLE: { label: "空闲", theme: "default" },
    LIVE: { label: "直播中", theme: "success" },
    MONITORING: { label: "监控中", theme: "default" },
    OFFLINE: { label: "未开播", theme: "default" },
    RECORDING: { label: "录制中", theme: "primary" },
};

export const LIVE_SOURCE_STATE_FILTER_OPTIONS = Object.entries(LIVE_SOURCE_STATE_META).map(([value, meta]) => ({
    label: meta.label,
    value,
}));

export function liveSourceStateMeta(state?: string | null): LiveSourceStateMeta {
    const meta = state ? LIVE_SOURCE_STATE_META[state as LiveSourceState] : undefined;

    return meta ?? { label: state ?? "未知", theme: "default" };
}

export function livePlatformLabel(platform?: string | null): string {
    return platform ? (LIVE_PLATFORM_LABELS[platform] ?? platform) : "—";
}

export function liveSourceDisplayName(source: LiveSource): string {
    return source.spec.alias?.trim() || source.status?.roomTitle?.trim() || source.metadata.name;
}

export function liveSourceRoomTitle(source: LiveSource): string | undefined {
    return source.status?.roomTitle?.trim() || undefined;
}

export function liveSourceAnchorName(source: LiveSource): string | undefined {
    return source.status?.anchorName?.trim() || undefined;
}

export function liveSourceRecordingName(source: LiveSource): string | undefined {
    return source.status?.currentRecording?.trim() || undefined;
}

export function liveSourceMatchesKeyword(source: LiveSource, keyword: string): boolean {
    if (!keyword) {
        return true;
    }

    const haystack = [
        source.metadata.name,
        source.spec.alias,
        source.spec.roomId,
        source.status?.roomTitle,
        source.status?.anchorName,
    ]
        .filter((value): value is string => Boolean(value))
        .join(" ")
        .toLowerCase();

    return haystack.includes(keyword.toLowerCase());
}
