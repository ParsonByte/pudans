import type { AiModel } from "./_dans";

export type ModelType = "chat" | "image" | "video" | "audio" | "embedding" | "3d" | "rerank";

export const MODEL_TYPE_OPTIONS: Array<{ label: string; value: ModelType }> = [
    { label: "对话", value: "chat" },
    { label: "图片", value: "image" },
    { label: "视频", value: "video" },
    { label: "音频", value: "audio" },
    { label: "向量化", value: "embedding" },
    { label: "3D", value: "3d" },
    { label: "重排", value: "rerank" },
];

export const MODEL_TYPE_LABELS: Record<string, string> = Object.fromEntries(
    MODEL_TYPE_OPTIONS.map((option) => [option.value, option.label]),
);

export function capabilityLabel(code?: string): string {
    if (!code) {
        return "未命名能力";
    }

    return MODEL_TYPE_LABELS[code] ?? code;
}

export type ModelTab = "all" | "chat" | "image" | "video" | "audio" | "embedding";

export const MODEL_TABS: ReadonlyArray<{ label: string; value: ModelTab }> = [
    { label: "全部", value: "all" },
    { label: "对话", value: "chat" },
    { label: "图片", value: "image" },
    { label: "视频", value: "video" },
    { label: "音频", value: "audio" },
    { label: "向量化", value: "embedding" },
];

export const MODEL_FEATURE_LABELS: Record<string, string> = {
    tools: "工具调用",
    vision: "视觉识别",
    reasoning: "深度思考",
    "web-search": "联网搜索",
    image: "图片生成",
    video: "视频识别",
    audio: "音频生成",
    "image-generation": "图片生成",
    "video-generation": "视频生成",
    "audio-generation": "音频生成",
    asr: "语音识别",
};

const MODALITY_LABELS: Record<string, string> = {
    audio: "音频",
    file: "文件",
    image: "图片",
    text: "文本",
    video: "视频",
};

export function modalityLabel(code: string): string {
    return MODALITY_LABELS[code] ?? code;
}

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stringList(value: unknown): string[] {
    if (!Array.isArray(value)) {
        return [];
    }

    return Array.from(new Set(value.filter((item): item is string => typeof item === "string")));
}

export function modelTypeOf(model?: AiModel): string {
    const capabilities = model?.capabilities ?? {};
    const supported = Object.keys(capabilities).find((key) => MODEL_TYPE_LABELS[key]);

    return supported ?? "unknown";
}

export function modelProvider(model?: AiModel): string {
    const catalog = modelCapabilityDetails(model).catalog;
    if (isRecord(catalog)) {
        const provider = catalog.provider;
        if (typeof provider === "string" && provider.trim()) {
            return provider.trim();
        }
    }
    const prefix = model?.logicalName?.split("/")[0]?.trim();

    return prefix || "unknown";
}

export function modelLabel(model?: AiModel): string {
    return model?.displayName ?? model?.logicalName ?? model?.id ?? "未命名模型";
}

export function modelCapabilityCodes(model?: AiModel): string[] {
    return Object.keys(model?.capabilities ?? {});
}

function modelCapabilityDetails(model?: AiModel): UnknownRecord {
    const capabilities = model?.capabilities ?? {};
    const entry = Object.entries(capabilities).find(([key]) => MODEL_TYPE_LABELS[key]);

    return entry && isRecord(entry[1]) ? entry[1] : {};
}

export function modelDescription(model?: AiModel): string | undefined {
    const description = model?.description?.trim();
    if (description) {
        return description;
    }
    const catalog = modelCapabilityDetails(model).catalog;
    if (!isRecord(catalog)) {
        return undefined;
    }
    const catalogDescription = catalog.description;

    return typeof catalogDescription === "string" && catalogDescription.trim() ? catalogDescription.trim() : undefined;
}

export function modelFeatures(model?: AiModel): string[] {
    return stringList(modelCapabilityDetails(model).features);
}

export function modelModalities(model?: AiModel): { input: string[]; output: string[] } {
    const modalities = modelCapabilityDetails(model).modalities;
    if (!isRecord(modalities)) {
        return { input: [], output: [] };
    }

    return {
        input: stringList(modalities.input),
        output: stringList(modalities.output),
    };
}

export function modelParameters(model?: AiModel): string[] {
    const schema = model?.parameterSchema;
    if (!isRecord(schema)) {
        return [];
    }

    return stringList(schema.parameters);
}

export function formatTokenLimit(value?: number | null): string {
    if (!value || value <= 0) {
        return "—";
    }
    if (value >= 1_048_576) {
        return `${Number((value / 1_048_576).toFixed(1))}M`;
    }
    if (value >= 1_024) {
        return `${Math.round(value / 1_024)}K`;
    }

    return String(value);
}
