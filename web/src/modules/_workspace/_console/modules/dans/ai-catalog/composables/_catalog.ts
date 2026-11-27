import type { CatalogEntry } from "../../composables/_dans";

export const CATALOG_KIND_OPTIONS = [
    { label: "全部目录", value: "all" },
    { label: "创作媒体模式", value: "creation_media_mode" },
    { label: "创作操作", value: "creation_operation" },
    { label: "创作参数", value: "creation_parameter_option" },
    { label: "理解维度", value: "understanding_dimension" },
    { label: "记忆类型", value: "memory_type" },
    { label: "记忆层级", value: "memory_layer" },
    { label: "资产类型", value: "asset_kind" },
    { label: "资产来源", value: "asset_source" },
    { label: "沙箱提供商", value: "sandbox_provider" },
    { label: "沙箱模板", value: "sandbox_template" },
    { label: "沙箱资源规格", value: "sandbox_resource_profile" },
    { label: "聊天回答模式", value: "chat_answer_mode" },
    { label: "聊天推理等级", value: "chat_reasoning_level" },
    { label: "探索分类", value: "explore_category" },
    { label: "探索筛选", value: "explore_filter" },
] as const;

export const CATALOG_STATUS_OPTIONS = [
    { label: "全部状态", value: "all" },
    { label: "启用", value: "enabled" },
    { label: "停用", value: "disabled" },
] as const;

export type CatalogEntryState = "active" | "disabled" | "expired" | "pending";

export function catalogKindLabel(kind?: string): string {
    return CATALOG_KIND_OPTIONS.find((item) => item.value === kind)?.label ?? kind ?? "未设置";
}

export function catalogEntryState(entry: CatalogEntry, now = Date.now()): CatalogEntryState {
    if (entry.enabled === false) {
        return "disabled";
    }

    const validFrom = entry.validFrom ? Date.parse(entry.validFrom) : Number.NaN;
    if (Number.isFinite(validFrom) && validFrom > now) {
        return "pending";
    }

    const validTo = entry.validTo ? Date.parse(entry.validTo) : Number.NaN;
    if (Number.isFinite(validTo) && validTo <= now) {
        return "expired";
    }

    return "active";
}

export function catalogEntryStateLabel(state: CatalogEntryState): string {
    if (state === "disabled") {
        return "停用";
    }
    if (state === "pending") {
        return "待生效";
    }
    if (state === "expired") {
        return "已失效";
    }

    return "启用";
}
