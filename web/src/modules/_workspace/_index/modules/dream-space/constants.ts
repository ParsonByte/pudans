import type { DreamMemoryMeta, DreamMode, MemoryEmotion, MemoryType } from "./types";

export const DREAM_PAGE_TITLE = "幻境空间";
export const DREAM_PAGE_SUBTITLE = "记忆星辰，照见现实";
export const WORLD_VERSION = 1;
export const TIMELINE_MONTHS = [
    "2024.12",
    "2025.01",
    "2025.02",
    "2025.03",
    "2025.04",
    "2025.05",
    "2025.06",
    "2025.07",
    "2025.08",
    "2025.09",
    "2025.10",
    "2025.11",
    "2025.12",
    "2026.01",
    "2026.02",
    "2026.03",
    "2026.04",
    "2026.05",
    "2026.06",
    "2026.07",
    "2026.08",
    "2026.09",
    "2026.10",
    "2026.11",
] as const;

export const TIMELINE_START = 0;
export const TIMELINE_END = TIMELINE_MONTHS.length - 1;
export const PLAYER_HEIGHT = 1.72;
export const PLAYER_RADIUS = 0.36;
export const WORLD_GRAVITY = 18;
export const SESSION_STORAGE_KEY = "pudans:dans-dream-session";
export const SNAPSHOT_STORAGE_PREFIX = "pudans:dans-dream-snapshot";

export const DREAM_MODES: ReadonlyArray<{ description: string; id: DreamMode; label: string }> = [
    { description: "行走于 Dream Layer", id: "dream", label: "梦境" },
    { description: "让记忆投射到大地", id: "reality", label: "现实" },
    { description: "重组线索生成星座", id: "inspiration", label: "灵感" },
];

export const MEMORY_TYPE_META: Record<MemoryType, DreamMemoryMeta> = {
    conversation: { color: "#56D9FF", label: "对话", shape: "圆" },
    creation: { color: "#B49BFF", label: "创作", shape: "菱" },
    insight: { color: "#FFC857", label: "灵感", shape: "星" },
    asset: { color: "#FF8EC7", label: "素材", shape: "方" },
    project: { color: "#79E6B8", label: "项目", shape: "环" },
    person: { color: "#F4A77B", label: "人物", shape: "点" },
};

export const MEMORY_EMOTION_META: Record<MemoryEmotion, { color: string; label: string }> = {
    wonder: { color: "#FFC857", label: "惊奇" },
    curiosity: { color: "#56D9FF", label: "好奇" },
    calm: { color: "#8EB7FF", label: "平静" },
    joy: { color: "#FF9FD2", label: "愉悦" },
    resolve: { color: "#79E6B8", label: "笃定" },
    melancholy: { color: "#9C8CFF", label: "怅然" },
};

export const WEATHER_LABELS = {
    aurora: "极光低语",
    clear: "静星无风",
    embers: "余烬微雨",
    mist: "雾潮",
    petals: "光瓣风",
    starfall: "星屑雨",
} as const;

export const GAME_KEY_HINTS = [
    { keys: "WASD", label: "行走" },
    { keys: "Shift", label: "奔跑" },
    { keys: "E", label: "交互" },
    { keys: "M", label: "小地图" },
    { keys: "Tab", label: "记忆列表" },
    { keys: "F", label: "全屏" },
] as const;
