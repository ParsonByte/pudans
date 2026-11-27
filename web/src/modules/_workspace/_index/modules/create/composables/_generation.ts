import {
    type AiCreationCatalog,
    type AiCreationMediaMode,
    type GenerationTaskResultView,
    type GenerationTaskStatusView,
    type GenerationTaskSummary,
    apiClient,
    axiosInstance,
} from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

import { csrfHeaders } from "@/modules/_auth/composables/_auth-login";

import { uploadWorkspaceAssets } from "../../../composables/_workspace-assets";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";
const CREATION_UPLOAD_MODULE = "creation";
const SUPPORTED_OPERATIONS = new Set([
    "text",
    "reference",
    "frame",
    "edit",
    "motion",
    "continue",
    "upscale",
    "watermark",
    "lip_sync",
    "sound_effect",
    "depth_map",
]);

export const CREATION_CATALOG_QUERY_KEY = ["index", "creation-catalog"] as const;
export const CREATION_TASKS_QUERY_KEY = ["index", "creation", "generation-tasks"] as const;

const ACTIVE_TASK_STATUSES = new Set([
    "queued",
    "dispatching",
    "provider_running",
    "downloading",
    "archiving",
    "retry_queued",
]);

export function isActiveGenerationTask(task: GenerationTaskSummary): boolean {
    return ACTIVE_TASK_STATUSES.has(task.status ?? "");
}

export type GenerationAspectRatio = {
    width: number;
    height: number;
};

function parseGenerationAspectRatio(value: unknown): GenerationAspectRatio | undefined {
    if (typeof value !== "string") {
        return undefined;
    }

    const [width, height] = value.split(":").map((part) => Number(part.trim()));

    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
        return undefined;
    }

    return { width, height };
}

export function resolveGenerationAspectRatio(
    settings: Record<string, object> | undefined,
    mediaType: string | undefined,
): GenerationAspectRatio {
    return (
        parseGenerationAspectRatio(settings?.ratio) ??
        (mediaType === "image" ? { width: 3, height: 4 } : { width: 16, height: 9 })
    );
}

// 生成中的任务保持轮询，完成后停止请求。
export function useCreationTasks(limit = 24) {
    return useQuery({
        queryFn: () => listMyGenerationTasks(limit),
        queryKey: [...CREATION_TASKS_QUERY_KEY, limit],
        refetchInterval: (query) => {
            const tasks = query.state.data ?? [];

            return tasks.some((task) => ACTIVE_TASK_STATUSES.has(task.status ?? "")) ? 3000 : false;
        },
    });
}

export type CreationSettingValues = {
    advancedSettings: {
        seed: number;
        stagger: boolean;
    };
    audioEnabled: boolean;
    audioSettings: {
        speed: number;
        volume: number;
    };
    multiCamera: boolean;
    quantity: number;
    settings: {
        background: string;
        duration: number;
        previewMode: boolean;
        ratio: string;
        resolution: string;
    };
};

export type CreationGenerationRequest = {
    files: File[];
    mediaMode: "image" | "video";
    modelId: string;
    operation: string;
    prompt: string;
    settings: Record<string, unknown>;
};

export async function getCreationCatalog(): Promise<AiCreationCatalog> {
    return (await apiClient.aiCatalog.getAiCreationCatalog()).data;
}

async function listMyGenerationTasks(limit = 8): Promise<GenerationTaskSummary[]> {
    const response = await axiosInstance.get<{ items?: GenerationTaskSummary[] }>(
        `${API_ROOT}/generationtasks/-/mine`,
        { params: { limit } },
    );

    return response.data.items ?? [];
}

export async function getGenerationTaskResult(taskId: string): Promise<GenerationTaskResultView> {
    return (
        await axiosInstance.get<GenerationTaskResultView>(
            `${API_ROOT}/generationtasks/${encodeURIComponent(taskId)}/result`,
        )
    ).data;
}

export async function getGenerationTaskStatus(taskId: string): Promise<GenerationTaskStatusView> {
    return (
        await axiosInstance.get<GenerationTaskStatusView>(
            `${API_ROOT}/generationtasks/${encodeURIComponent(taskId)}/status`,
        )
    ).data;
}

export function resolveCreationOperation(
    mode: AiCreationMediaMode | undefined,
    toolMode: string,
    hasInput: boolean,
): string | null {
    if (!mode || toolMode === "audio") {
        return null;
    }
    if (toolMode !== "default" && !SUPPORTED_OPERATIONS.has(toolMode)) {
        return null;
    }

    const operation = toolMode === "default" ? (hasInput ? "reference" : "text") : toolMode;
    const supported = new Set((mode.operations ?? []).flatMap((item) => (item.key ? [item.key] : [])));

    return supported.size === 0 || supported.has(operation) ? operation : null;
}

export function creationSettings(input: CreationSettingValues): Record<string, unknown> {
    const settings: Record<string, unknown> = {
        background: input.settings.background,
        duration: input.settings.duration,
        multiCamera: input.multiCamera,
        previewMode: input.settings.previewMode,
        quantity: input.quantity,
        ratio: input.settings.ratio,
        resolution: input.settings.resolution,
        stagger: input.advancedSettings.stagger,
    };

    if (input.advancedSettings.seed > 0) {
        settings.seed = input.advancedSettings.seed;
    }
    if (input.audioEnabled) {
        settings.audio = true;
        settings.audioSpeed = input.audioSettings.speed;
        settings.audioVolume = input.audioSettings.volume;
    }

    return settings;
}

export async function startCreationGeneration(request: CreationGenerationRequest): Promise<GenerationTaskSummary> {
    const inputAssetIds = await uploadWorkspaceAssets(request.files, {
        module: CREATION_UPLOAD_MODULE,
        source: "creation",
    });
    const response = await axiosInstance.post<GenerationTaskSummary>(
        `${API_ROOT}/generationtasks/-/start`,
        {
            inputAssetIds,
            mediaType: request.mediaMode,
            modelId: request.modelId,
            operation: request.operation,
            prompt: request.prompt,
            settings: request.settings,
        },
        {
            headers: {
                "Idempotency-Key": crypto.randomUUID(),
                ...csrfHeaders(),
            },
        },
    );

    if (!response.data.id) {
        throw new Error("生成任务响应缺少任务标识。");
    }

    return response.data;
}
