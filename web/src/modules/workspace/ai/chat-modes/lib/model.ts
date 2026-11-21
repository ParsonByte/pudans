import type { KnowledgeBaseItem } from "@/modules/workspace/ai/knowledge/lib/api";
import type { MultiSelectOption } from "@/modules/workspace/ai/library/components/multi-select";
import type { AiRouteGroupItem } from "@/modules/workspace/ai/providers/lib/api";
import { MODEL_TIER_OPTIONS, optionLabel } from "@/modules/workspace/ai/providers/lib/model";

import type { AiChatModeItem, AiChatModeTier } from "./api";

const BUILTIN_CHAT_MODE_NAMES = new Set(["fast", "pro"]);

export const CHAT_MODE_TIER_OPTIONS: { value: AiChatModeTier; label: string }[] = [
  { value: "AUTO", label: "自动" },
  ...MODEL_TIER_OPTIONS,
];

export const CHAT_MODE_TIER_VALUES = CHAT_MODE_TIER_OPTIONS.map((option) => option.value) as [
  AiChatModeTier,
  ...AiChatModeTier[],
];

type ValueOption = {
  value: string;
  label: string;
  hint?: string;
};

export function isBuiltinChatMode(name: string): boolean {
  return BUILTIN_CHAT_MODE_NAMES.has(name);
}

export function chatModeLabel(mode: AiChatModeItem): string {
  return mode.spec.displayName || mode.metadata.name;
}

export function chatModeTierLabel(tier: AiChatModeTier | undefined): string {
  return optionLabel(CHAT_MODE_TIER_OPTIONS, tier, "自动");
}

export function sortChatModes(modes: readonly AiChatModeItem[]): AiChatModeItem[] {
  return [...modes].sort(
    (left, right) =>
      (left.spec.sortOrder ?? 0) - (right.spec.sortOrder ?? 0) ||
      chatModeLabel(left).localeCompare(chatModeLabel(right), "zh-CN"),
  );
}

export function withCurrentOption(
  options: readonly ValueOption[],
  current: string | undefined,
  missingLabel = "不存在",
): ValueOption[] {
  const result = [...options];
  if (current && !result.some((option) => option.value === current)) {
    result.push({ value: current, label: `${current}（${missingLabel}）` });
  }
  return result;
}

export function routeGroupOptions(
  routeGroups: readonly AiRouteGroupItem[],
  current?: string,
): ValueOption[] {
  return withCurrentOption(
    routeGroups.map((group) => ({
      value: group.metadata.name,
      label: group.spec.displayName || group.metadata.name,
      hint: group.metadata.name,
    })),
    current,
  );
}

export function knowledgeBaseOptions(
  knowledgeBases: readonly KnowledgeBaseItem[],
  selected: readonly string[],
): MultiSelectOption[] {
  const options: MultiSelectOption[] = knowledgeBases.map((base) => ({
    value: base.metadata.name,
    label: base.spec.displayName || base.metadata.name,
    hint: base.metadata.name,
  }));
  const known = new Set(options.map((option) => option.value));
  for (const code of selected) {
    if (!known.has(code)) {
      options.push({ value: code, label: `${code}（不存在）` });
    }
  }
  return options;
}
