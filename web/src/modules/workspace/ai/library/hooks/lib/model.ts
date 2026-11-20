import type { HookEvent, HookItem } from "./api";

export const HOOK_EVENT_LABELS: Record<HookEvent, string> = {
  SESSION_START: "会话开始",
  USER_PROMPT_SUBMIT: "提交用户消息",
  PRE_TOOL_USE: "工具调用前",
  POST_TOOL_USE: "工具调用后",
  SESSION_END: "会话结束",
};

export const HOOK_EVENT_HINTS: Record<HookEvent, string> = {
  SESSION_START: "Agent 会话创建时触发。",
  USER_PROMPT_SUBMIT: "用户提交新的提示词时触发。",
  PRE_TOOL_USE: "Agent 调用工具前触发，可按工具名过滤。",
  POST_TOOL_USE: "Agent 调用工具后触发，可按工具名过滤。",
  SESSION_END: "Agent 会话结束时触发。",
};

export function hookDisplayName(hook: HookItem): string {
  return hook.spec.displayName || hook.metadata.name;
}

export function hookMatcherLabel(hook: HookItem): string {
  return hook.spec.matcher || "匹配全部";
}
