export const DEFAULT_SESSION_PAGE_SIZE = 20;

export const CONVERSATION_MESSAGE_PAGE_SIZE = 50;

const SESSION_STATUS_ACTIVE = "ACTIVE";

const SESSION_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "活跃",
  DELETED: "已删除",
};

const CHAT_MODE_LABELS: Record<string, string> = {
  fast: "快速",
  pro: "进阶",
};

const MESSAGE_ROLE_LABELS: Record<string, string> = {
  user: "用户",
  assistant: "助手",
  system: "系统",
  tool: "工具",
};

export function conversationTitle(conversation: { id: number; title?: string | null }): string {
  const title = conversation.title?.trim();
  return title || `会话 #${conversation.id}`;
}

export function conversationModeLabel(modeCode?: string | null): string {
  if (!modeCode) {
    return "—";
  }
  return CHAT_MODE_LABELS[modeCode] ?? modeCode;
}

export function sessionStatusLabel(status?: string | null): string {
  if (!status) {
    return "—";
  }
  return SESSION_STATUS_LABELS[status] ?? status;
}

export function sessionStatusBadgeVariant(status?: string | null): "secondary" | "outline" {
  return status === SESSION_STATUS_ACTIVE ? "secondary" : "outline";
}

export function messageRoleLabel(role?: string | null): string {
  if (!role) {
    return "未知";
  }
  return MESSAGE_ROLE_LABELS[role] ?? role;
}
