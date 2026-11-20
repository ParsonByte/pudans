import type { SkillItem, SkillSource } from "./api";

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

export function skillDisplayName(skill: SkillItem): string {
  return skill.spec.displayName || skill.metadata.name;
}
