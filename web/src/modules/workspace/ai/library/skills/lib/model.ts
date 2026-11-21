import type { SkillItem, SkillSource, SkillStatus } from "./api";

export const SKILL_SOURCE_LABELS: Record<SkillSource, string> = {
  INLINE: "内联",
  URL: "URL",
  GIT: "Git",
};

export const SKILL_SOURCE_HINTS: Record<SkillSource, string> = {
  INLINE: "直接在控制台编写 SKILL.md 内容。",
  URL: "从 HTTP(S) 地址读取 SKILL.md，可先导入草稿再保存。",
  GIT: "从 Git 仓库路径读取 SKILL.md，需填写仓库地址与引用。",
};

export const SKILL_STATUS_LABELS: Record<SkillStatus, string> = {
  DRAFT: "草稿",
  PUBLISHED: "已发布",
  ARCHIVED: "已归档",
};

export const SKILL_USAGE_STATE_LABELS: Record<"ACTIVE" | "ARCHIVED", string> = {
  ACTIVE: "活跃",
  ARCHIVED: "已归档",
};

const SKILL_AUDIT_OPERATION_LABELS: Record<string, string> = {
  promote: "晋升",
  archive: "归档",
};

const SKILL_AUDIT_DECISION_LABELS: Record<string, string> = {
  approved: "通过",
  rejected: "拒绝",
  deferred: "延后",
  invalid: "无效",
  archived: "已归档",
};

export function skillDisplayName(skill: SkillItem): string {
  return skill.spec.displayName || skill.metadata.name;
}

export function skillStatus(skill: SkillItem): SkillStatus {
  return skill.spec.status ?? "PUBLISHED";
}

export function skillStatusBadgeVariant(status: SkillStatus): "secondary" | "outline" {
  return status === "PUBLISHED" ? "secondary" : "outline";
}

export function skillAuditOperationLabel(operation: string): string {
  return SKILL_AUDIT_OPERATION_LABELS[operation] ?? operation;
}

export function skillAuditDecisionLabel(decision: string): string {
  return SKILL_AUDIT_DECISION_LABELS[decision] ?? decision;
}
