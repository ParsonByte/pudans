export type ComposerMediaMode = "audio" | "image" | "video";

const VIDEO_TOOL_MODES = ["text", "reference", "frame", "edit", "motion", "continue"] as const;

const IMAGE_TOOL_MODES = ["default"] as const;

const AUDIO_TOOL_MODES = ["default"] as const;

export type ComposerToolMode =
    (typeof VIDEO_TOOL_MODES)[number] | (typeof IMAGE_TOOL_MODES)[number] | (typeof AUDIO_TOOL_MODES)[number];

export const COMPOSER_BACKGROUND_OPTIONS = [
    { label: "自动", value: "auto" },
    { label: "透明", value: "transparent" },
    { label: "不透明", value: "opaque" },
] as const;

export type ComposerBackground = (typeof COMPOSER_BACKGROUND_OPTIONS)[number]["value"];

export type ComposerSettingsValue = {
    background: ComposerBackground;
    duration: number;
    previewMode: boolean;
    ratio: string;
    resolution: string;
};

export type ComposerAdvancedSettingsValue = {
    seed: number;
    stagger: boolean;
};

export type ComposerAudioSettingsValue = {
    speed: number;
    volume: number;
};

export type ComposerModelValue = "dans";

export const DEFAULT_COMPOSER_MEDIA_MODE: ComposerMediaMode = "video";
export const DEFAULT_COMPOSER_TOOL_MODE: ComposerToolMode = "text";
const DEFAULT_COMPOSER_IMAGE_TOOL_MODE: ComposerToolMode = "default";

export const COMPOSER_RESOLUTION_OPTIONS = ["360P", "540P", "720P", "1080P"] as const;
export const COMPOSER_IMAGE_RESOLUTION_OPTIONS = ["1080P", "1.5K", "2K"] as const;
export const DEFAULT_COMPOSER_VIDEO_RESOLUTION = "720P";
export const DEFAULT_COMPOSER_IMAGE_RESOLUTION = "1080P";
export const COMPOSER_RATIO_OPTIONS = ["16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "3:2", "2:3"] as const;
export const COMPOSER_IMAGE_RATIO_OPTIONS = [
    "自动",
    "1:1",
    "16:9",
    "9:16",
    "4:3",
    "3:4",
    "5:4",
    "4:5",
    "3:2",
    "2:3",
    "21:9",
] as const;
export const COMPOSER_MIN_DURATION = 1;
export const COMPOSER_MAX_DURATION = 15;
export const COMPOSER_MAX_SEED = 999999999;

export const DEFAULT_COMPOSER_SETTINGS: ComposerSettingsValue = {
    background: "auto",
    duration: 5,
    previewMode: false,
    ratio: "16:9",
    resolution: DEFAULT_COMPOSER_VIDEO_RESOLUTION,
};

export const DEFAULT_COMPOSER_ADVANCED_SETTINGS: ComposerAdvancedSettingsValue = {
    seed: 0,
    stagger: false,
};

export const DEFAULT_COMPOSER_AUDIO_SETTINGS: ComposerAudioSettingsValue = {
    speed: 1,
    volume: 50,
};

export const COMPOSER_AUDIO_EMOTION_TAGS = ["悲伤", "惊叹", "深沉大声呐喊", "颤抖", "愤怒", "兴奋"] as const;
export const COMPOSER_AUDIO_ACTION_TAGS = ["倒吸一口气", "叹息", "清嗓", "咯咯笑", "大笑", "咳嗽"] as const;
export const COMPOSER_AUDIO_TAGS = [...COMPOSER_AUDIO_EMOTION_TAGS, ...COMPOSER_AUDIO_ACTION_TAGS];

export function getComposerToolModes(mediaMode: ComposerMediaMode): readonly ComposerToolMode[] {
    if (mediaMode === "video") {
        return VIDEO_TOOL_MODES;
    }

    return mediaMode === "image" ? IMAGE_TOOL_MODES : AUDIO_TOOL_MODES;
}

export function getDefaultComposerToolMode(mediaMode: ComposerMediaMode): ComposerToolMode {
    return mediaMode === "video" ? DEFAULT_COMPOSER_TOOL_MODE : DEFAULT_COMPOSER_IMAGE_TOOL_MODE;
}

export function getComposerBackgroundLabel(background: ComposerBackground): string {
    return (
        COMPOSER_BACKGROUND_OPTIONS.find((option) => option.value === background)?.label ??
        COMPOSER_BACKGROUND_OPTIONS[0].label
    );
}
