export type AuditMode = "WHITELIST" | "BLACKLIST";

type AuditEventOption = {
  value: string;
  label: string;
};

export type AuditEventGroup = {
  key: string;
  label: string;
  description: string;
  items: readonly AuditEventOption[];
};

export const AUDIT_MODE_OPTIONS: readonly { value: AuditMode; label: string }[] = [
  { value: "WHITELIST", label: "仅记录勾选的事件" },
  { value: "BLACKLIST", label: "不记录勾选的事件" },
];

export const AUDIT_OPERATION_GROUPS: readonly AuditEventGroup[] = [
  {
    key: "auth",
    label: "认证与会话",
    description: "登录、登出、通行密钥和账号绑定相关的事件。",
    items: [
      { value: "LOGIN", label: "登录（含失败）" },
      { value: "LOGOUT", label: "登出" },
      { value: "PASSKEY", label: "通行密钥操作" },
      { value: "BIND", label: "账号绑定" },
    ],
  },
  {
    key: "access",
    label: "审计与访问",
    description: "审计查询、页面访问和只读接口调用相关的事件。",
    items: [
      { value: "AUDIT_QUERY", label: "审计日志查询" },
      { value: "NAVIGATION", label: "页面访问" },
      { value: "READ", label: "数据读取" },
    ],
  },
  {
    key: "mutation",
    label: "数据变更",
    description: "创建、更新和删除数据相关的事件。",
    items: [
      { value: "CREATE", label: "创建" },
      { value: "UPDATE", label: "更新" },
      { value: "DELETE", label: "删除" },
    ],
  },
  {
    key: "other",
    label: "其他事件",
    description: "无法归类的操作事件。",
    items: [{ value: "UNKNOWN", label: "未知操作" }],
  },
];

export const AUDIT_RESOURCE_GROUPS: readonly AuditEventGroup[] = [
  {
    key: "user-permission",
    label: "用户与权限",
    description: "用户、用户组、角色和绑定关系相关的事件。",
    items: [
      { value: "User", label: "用户" },
      { value: "UserGroup", label: "用户组" },
      { value: "UserGroupBinding", label: "用户组绑定" },
      { value: "Role", label: "角色" },
      { value: "RoleBinding", label: "角色绑定" },
      { value: "TempRoleBinding", label: "临时角色绑定" },
    ],
  },
  {
    key: "identity-security",
    label: "身份与安全",
    description: "通行密钥、外部账号、访问令牌和风险规则相关的事件。",
    items: [
      { value: "Passkey", label: "通行密钥" },
      { value: "UserConnection", label: "外部账号" },
      { value: "PersonalAccessToken", label: "个人访问令牌" },
      { value: "RiskRule", label: "风险规则" },
    ],
  },
  {
    key: "config-integration",
    label: "配置与集成",
    description: "参数设置、系统设置和 Webhook 相关的事件。",
    items: [
      { value: "ParameterSetting", label: "参数设置" },
      { value: "Setting", label: "系统设置" },
      { value: "Webhook", label: "Webhook" },
    ],
  },
  {
    key: "audit",
    label: "审计",
    description: "审计记录相关的事件。",
    items: [{ value: "AuditRecord", label: "审计记录" }],
  },
  {
    key: "request",
    label: "其他请求",
    description: "无法归类的请求资源。",
    items: [{ value: "Request", label: "其他请求" }],
  },
];

export const AUDIT_OPERATION_VALUES = AUDIT_OPERATION_GROUPS.flatMap((group) =>
  group.items.map((item) => item.value),
);
