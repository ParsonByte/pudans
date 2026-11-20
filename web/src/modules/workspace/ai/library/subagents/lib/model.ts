import type { SubagentItem } from "./api";

export function subagentDisplayName(subagent: SubagentItem): string {
  return subagent.spec.displayName || subagent.metadata.name;
}

export function subagentPromptExcerpt(subagent: SubagentItem): string {
  const firstLine = subagent.spec.prompt
    .split("\n")
    .map((line) => line.trim())
    .find((line) => line.length > 0);
  return firstLine || "未填写提示词";
}

export function subagentTargetLabel(subagent: SubagentItem): string {
  const parts = [subagent.spec.routeGroup, subagent.spec.model].filter(Boolean);
  return parts.length > 0 ? parts.join(" · ") : "默认路由";
}
