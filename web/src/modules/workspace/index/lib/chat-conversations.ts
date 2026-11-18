export type ChatConversation = {
  id: string;
  title: string;
  unread?: boolean;
  pinned?: boolean;
};

export const CHAT_CONVERSATIONS: ChatConversation[] = [
  { id: "new", title: "新的对话", pinned: true },
  { id: "feishu-sync", title: "飞书身份同步" },
  { id: "role-permissions", title: "角色与权限设计" },
  { id: "audit-logs", title: "审计日志排查" },
  { id: "console-workspace", title: "Console 工作区规划" },
  { id: "login-risk", title: "登录协议风险评估", unread: true },
  { id: "session-timeout", title: "会话超时策略" },
  { id: "identity-providers", title: "身份提供商接入" },
  { id: "permission-templates", title: "权限模板梳理" },
];

export function findChatConversation(id: string): ChatConversation | undefined {
  return CHAT_CONVERSATIONS.find((conversation) => conversation.id === id);
}
