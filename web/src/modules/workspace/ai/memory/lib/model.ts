import { beijingDateTimeInputToIso, beijingDateTimeInputValue } from "@/lib/date";

import type {
  MemoryAssetView,
  MemoryAssetVisibility,
  MemoryImportMessage,
  MemoryLayer,
  MemoryPromptLayer,
  MemoryPromptTargetType,
} from "./api";

export const MEMORY_PANEL_HEIGHT_CLASS = "h-[60vh] min-[960px]:h-[calc(100vh-22rem)]";

export const MEMORY_LAYER_META: readonly {
  id: MemoryLayer;
  label: string;
  description: string;
}[] = [
  { id: "L0", label: "L0 对话", description: "原始会话消息" },
  { id: "L1", label: "L1 原子", description: "抽取的原子记忆" },
  { id: "L2", label: "L2 场景", description: "聚合的场景摘要" },
  { id: "L3", label: "L3 核心", description: "长期核心画像" },
];

export const MEMORY_LAYER_PAGE_SIZE: Record<MemoryLayer, number> = {
  L0: 20,
  L1: 20,
  L2: 50,
  L3: 1,
};

export const MEMORY_TIME_FILTER_LAYERS: ReadonlySet<MemoryLayer> = new Set(["L0", "L1"]);

export const MEMORY_IMPORT_LIMIT = 100;

export const MEMORY_BINDING_LIMIT = 2;

export const MEMORY_CLEAR_CONFIRM_TEXT = "清空";

export const MEMORY_VISIBILITY_LABELS: Record<MemoryAssetVisibility, string> = {
  TEAM: "团队可见",
  PRIVATE: "仅自己",
};

export const MEMORY_TYPE_LABELS: Record<string, string> = {
  PERSONA: "画像",
  EPISODIC: "情景",
  INSTRUCTION: "指令",
  WORK_FACT: "工作事实",
  WORK_TASK: "工作任务",
  WORK_METHOD: "工作方法",
  WORK_ARTIFACT: "工作产出",
};

export const MEMORY_SCOPE_LABELS: Record<string, string> = {
  PRIVATE: "私有",
  TEAM: "团队",
  RESTRICTED: "受限",
};

export const MEMORY_PROMPT_SOURCE_LABELS: Record<string, string> = {
  SYSTEM: "系统默认",
  INSTANCE: "实例策略",
  TEAM: "团队策略",
  AGENT: "Agent 策略",
};

export const MEMORY_PROMPT_TARGET_LABELS: Record<MemoryPromptTargetType, string> = {
  INSTANCE: "实例",
  TEAM: "团队",
  AGENT: "Agent",
};

export const MEMORY_PROMPT_ACTION_LABELS: Record<string, string> = {
  APPLY: "应用",
  CLEAR: "清除",
  UPDATE: "更新",
};

export const MEMORY_PROMPT_LAYER_OPTIONS: readonly MemoryPromptLayer[] = ["L1", "L2", "L3"];

export const MEMORY_LOG_SOURCE_OPTIONS: readonly { value: string; label: string }[] = [
  { value: "system", label: "系统默认" },
  { value: "instance", label: "实例策略" },
  { value: "team", label: "团队策略" },
  { value: "agent", label: "Agent 策略" },
];

export type MemoryTimeRange = {
  from: string;
  to: string;
};

export type MemoryContentLike = {
  content?: string | null;
  body?: string | null;
  personaText?: string | null;
};

export type MemoryTimeLike = {
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type MemoryImportParseResult =
  { ok: true; messages: MemoryImportMessage[] } | { ok: false; error: string };

export function memoryAssetTitle(asset: MemoryAssetView): string {
  return asset.displayName || asset.name;
}

export function memoryAssetTimestamp(asset: MemoryAssetView): string | undefined {
  return asset.updatedAt ?? asset.createdAt ?? undefined;
}

export function isMemoryAssetHidden(
  asset: MemoryAssetView,
  currentUserId: string | undefined,
): boolean {
  return (
    asset.visibility === "PRIVATE" && Boolean(currentUserId) && asset.ownerUserId !== currentUserId
  );
}

export function memoryItemContent(item: MemoryContentLike): string {
  return item.content ?? item.body ?? item.personaText ?? "";
}

function memoryItemTimestamp(item: MemoryTimeLike): string | undefined {
  return item.createdAt ?? item.updatedAt ?? undefined;
}

export function sortMemoryItemsAscending<T extends { id?: number | string } & MemoryTimeLike>(
  items: readonly T[],
): T[] {
  return [...items].sort((left, right) => {
    if (typeof left.id === "number" && typeof right.id === "number") {
      return left.id - right.id;
    }
    const leftTime = memoryItemTimestamp(left);
    const rightTime = memoryItemTimestamp(right);
    if (leftTime && rightTime) {
      return new Date(leftTime).getTime() - new Date(rightTime).getTime();
    }
    return 0;
  });
}

export function defaultMemoryTimeRange(): MemoryTimeRange {
  const to = new Date();
  const from = new Date(to.getTime() - 24 * 60 * 60 * 1000);
  return { from: beijingDateTimeInputValue(from), to: beijingDateTimeInputValue(to) };
}

export function toIsoDateTime(value: string): string | undefined {
  return beijingDateTimeInputToIso(value);
}

export function stripScenarioMeta(content: string): string {
  const trimmed = content.trimStart();
  for (const [open, close] of [
    ["[META]", "[/META]"],
    ["<!--", "-->"],
  ] as const) {
    if (trimmed.startsWith(open)) {
      const end = trimmed.indexOf(close, open.length);
      if (end >= 0 && end <= 2000) {
        return trimmed.slice(end + close.length).replace(/^\n/, "");
      }
      return content;
    }
  }
  if (trimmed.startsWith("---\n")) {
    const end = trimmed.indexOf("\n---", 4);
    if (end > 0 && end <= 2000 && trimmed.slice(4, end).includes(":")) {
      return trimmed.slice(end + 4).replace(/^\n/, "");
    }
  }
  return content;
}

export function parseMemoryImportPayload(
  payload: string,
  allowPlainText: boolean,
): MemoryImportParseResult {
  const trimmed = payload.trim();
  if (!trimmed) {
    return { ok: false, error: "请粘贴 JSON 或选择文件。" };
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    if (allowPlainText) {
      return { ok: true, messages: [{ role: "user", content: trimmed }] };
    }
    return { ok: false, error: "JSON 解析失败，请检查格式。" };
  }
  if (!Array.isArray(parsed)) {
    return { ok: false, error: "顶层必须是消息数组。" };
  }
  if (parsed.length === 0) {
    return { ok: false, error: "消息数组不能为空。" };
  }
  if (parsed.length > MEMORY_IMPORT_LIMIT) {
    return {
      ok: false,
      error: `最多导入 ${MEMORY_IMPORT_LIMIT} 条消息，当前 ${parsed.length} 条。`,
    };
  }
  const messages: MemoryImportMessage[] = [];
  for (let index = 0; index < parsed.length; index += 1) {
    const entry = parsed[index];
    if (typeof entry !== "object" || entry === null || Array.isArray(entry)) {
      return { ok: false, error: `第 ${index + 1} 条消息不是对象。` };
    }
    const record = entry as Record<string, unknown>;
    const role = record.role;
    const content = record.content;
    if (role !== "user" && role !== "assistant") {
      return {
        ok: false,
        error: `第 ${index + 1} 条消息的 role 必须是 user 或 assistant。`,
      };
    }
    if (typeof content !== "string" || content.trim().length === 0) {
      return { ok: false, error: `第 ${index + 1} 条消息的 content 不能为空。` };
    }
    messages.push({ role, content });
  }
  return { ok: true, messages };
}
