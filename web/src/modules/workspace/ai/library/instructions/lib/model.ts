import type { InstructionItem, InstructionScope } from "./api";

export const INSTRUCTION_SCOPE_LABELS: Record<InstructionScope, string> = {
  SHARED: "共享",
  AGENT: "指定 Agent",
};

export const INSTRUCTION_SCOPE_HINTS: Record<InstructionScope, string> = {
  SHARED: "所有 Agent 的系统指令中都会包含这段内容。",
  AGENT: "仅对已绑定的 Agent 生效。",
};

export function instructionDisplayName(instruction: InstructionItem): string {
  return instruction.spec.displayName || instruction.metadata.name;
}

export function instructionContentExcerpt(instruction: InstructionItem): string {
  const firstLine = instruction.spec.content
    .split("\n")
    .map((line) => line.trim())
    .find((line) => line.length > 0);
  return firstLine || "未填写内容";
}
