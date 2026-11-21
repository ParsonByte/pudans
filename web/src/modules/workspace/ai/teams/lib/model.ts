import type { ContentPreview } from "@/components/content/content-preview";
import { formatBeijingDateTime } from "@/lib/date";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";

import type {
  AiTeamItem,
  AiTeamMember,
  AiTeamMemberStatus,
  AiTeamQuota,
  AiTeamRole,
  AiTeamStatus,
} from "./api";

export const DEFAULT_TEAM_NAME = "default";

const TEAM_STATUS_LABELS: Record<AiTeamStatus, string> = {
  ACTIVE: "使用中",
  ARCHIVED: "已归档",
};

const TEAM_ROLE_LABELS: Record<AiTeamRole, string> = {
  ADMIN: "管理员",
  MEMBER: "成员",
  REVIEWER: "审核者",
};

const MEMBER_STATUS_LABELS: Record<AiTeamMemberStatus, string> = {
  ACTIVE: "使用中",
  REMOVED: "已移除",
};

export const TEAM_STATUS_OPTIONS = (
  Object.entries(TEAM_STATUS_LABELS) as [AiTeamStatus, string][]
).map(([value, label]) => ({ value, label }));

export const TEAM_ROLE_OPTIONS = (Object.entries(TEAM_ROLE_LABELS) as [AiTeamRole, string][]).map(
  ([value, label]) => ({ value, label }),
);

export function isBuiltinTeam(name: string): boolean {
  return name === DEFAULT_TEAM_NAME;
}

export function teamStatus(team: AiTeamItem): AiTeamStatus {
  return team.spec.status ?? "ACTIVE";
}

export function teamDisplayName(team: AiTeamItem): string {
  return team.spec.displayName || team.metadata.name;
}

export function teamStatusLabel(status: AiTeamStatus | undefined): string {
  return TEAM_STATUS_LABELS[status ?? "ACTIVE"];
}

export function teamRoleLabel(role: AiTeamRole | undefined): string {
  return TEAM_ROLE_LABELS[role ?? "MEMBER"];
}

export function memberStatusLabel(status: AiTeamMemberStatus | undefined): string {
  return MEMBER_STATUS_LABELS[status ?? "ACTIVE"];
}

function activeMembers(team: AiTeamItem): AiTeamMember[] {
  return (team.spec.members ?? []).filter((member) => member.status !== "REMOVED");
}

function activeMemberCount(team: AiTeamItem): number {
  return activeMembers(team).length;
}

export function teamMembersSummary(team: AiTeamItem): string {
  return `${activeMemberCount(team)} / ${team.spec.members?.length ?? 0}`;
}

export function teamQuotaLabel(quota: AiTeamQuota | undefined): string {
  const parts: string[] = [];
  if (quota?.limit != null) {
    parts.push(`额度 ${formatNumber(quota.limit)}`);
  }
  if (quota?.windowDays != null) {
    parts.push(`${quota.windowDays} 天窗口`);
  }
  if (quota?.resetsAt) {
    parts.push(`重置 ${formatBeijingDateTime(quota.resetsAt)}`);
  }
  return parts.length > 0 ? parts.join(" · ") : "未设置";
}

export function teamPreview(team: AiTeamItem): ContentPreview {
  const members = team.spec.members ?? [];
  const lines = [
    `# ${teamDisplayName(team)}`,
    "",
    `- 团队 ID：${team.metadata.name}`,
    `- 状态：${teamStatusLabel(teamStatus(team))}`,
    `- 负责人：${team.spec.ownerUserId}`,
    `- 默认 Agent：${team.spec.defaultAgent || "未设置"}`,
    `- 配额：${teamQuotaLabel(team.spec.quota)}`,
    `- 成员：${activeMemberCount(team)} 位使用中，共 ${members.length} 位`,
  ];
  if (team.spec.description) {
    lines.push("", team.spec.description);
  }
  lines.push("", "## 成员");
  if (members.length === 0) {
    lines.push("暂无成员。");
  } else {
    for (const member of members) {
      lines.push(
        `- ${member.userId} · ${teamRoleLabel(member.role)} · ${memberStatusLabel(member.status)}`,
      );
    }
  }
  return {
    title: teamDisplayName(team),
    description: `团队 ${team.metadata.name} · ${teamStatusLabel(teamStatus(team))}`,
    content: lines.join("\n"),
  };
}
