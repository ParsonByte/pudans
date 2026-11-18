import {
  AUDIT_OPERATION_GROUPS,
  AUDIT_RESOURCE_GROUPS,
} from "@/modules/workspace/console/settings/lib/audit-events";

const BASE_OPERATION_LABELS = new Map(
  AUDIT_OPERATION_GROUPS.flatMap((group) =>
    group.items.map((item) => [item.value, item.label] as const),
  ),
);

const EXTRA_OPERATION_LABELS: Record<string, string> = {
  LOGIN: "登录",
  LOGOUT: "登出",
  PASSKEY: "通行密钥操作",
  BIND: "账号绑定",
  AUDIT_QUERY: "审计查询",
  NAVIGATION: "页面访问",
  READ: "数据读取",
  CREATE: "创建",
  UPDATE: "更新",
  DELETE: "删除",
  UNKNOWN: "未知操作",
  RISK_BLOCKED: "风险拦截",
  RISK_ALERT: "风险告警",
  RISK_UNBAN: "解除风险封禁",
  PAT_CREATE: "创建访问令牌",
  PAT_REVOKE: "撤销访问令牌",
  PAT_DENIED: "访问令牌请求被拒绝",
  TEMP_ROLE_APPLY: "申请临时角色",
  TEMP_ROLE_GRANT: "授予临时角色",
  TEMP_ROLE_REVOKE: "撤销临时角色",
  TEMP_ROLE_EXPIRE: "临时角色到期",
  BATCH_CREATE_USER: "批量创建用户",
  USER_CREATE: "创建用户",
  TOTP_LOGIN_SUCCESS: "TOTP 登录成功",
  TOTP_LOGIN_FAILED: "TOTP 登录失败",
  TOTP_BIND: "绑定 TOTP",
  TOTP_UNBIND: "解绑 TOTP",
  WEBHOOK_DELIVER: "Webhook 投递",
};

const BASE_RESOURCE_LABELS = new Map(
  AUDIT_RESOURCE_GROUPS.flatMap((group) =>
    group.items.map((item) => [item.value, item.label] as const),
  ),
);

const EXTRA_RESOURCE_LABELS: Record<string, string> = {
  RiskControl: "风险控制",
  AuditRecord: "审计记录",
  Page: "页面",
  Request: "其他请求",
  AuthProvider: "身份提供商",
  UserPreference: "用户偏好",
  AnnotationSetting: "注解设置",
  Device: "设备",
  RememberMeToken: "记住我令牌",
  Secret: "密钥",
  navigation: "页面访问",
  login: "登录",
  logout: "登出",
  session: "会话",
  setup: "系统初始化",
  oauth2: "OAuth2",
  users: "用户",
  usergroups: "用户组",
  roles: "角色",
  riskrules: "风险规则",
  auditlogs: "审计日志",
  webhooks: "Webhook",
  authproviders: "身份提供商",
  avatars: "头像",
  "user-preferences": "用户偏好",
  "parameter-settings": "参数设置",
};

export function auditOperationLabel(value?: string): string {
  if (!value) {
    return "未知操作";
  }
  return EXTRA_OPERATION_LABELS[value] ?? BASE_OPERATION_LABELS.get(value) ?? value;
}

export function auditResourceLabel(value?: string): string {
  if (!value) {
    return "—";
  }
  return EXTRA_RESOURCE_LABELS[value] ?? BASE_RESOURCE_LABELS.get(value) ?? value;
}

export function auditResultLabel(value?: string): string {
  switch (value) {
    case "SUCCESS":
      return "成功";
    case "FAILURE":
    case "FAILED":
      return "失败";
    case "DENIED":
      return "拒绝";
    default:
      return value || "未知";
  }
}

const OPERATION_LABEL_VALUES = new Map<string, string>([
  ...BASE_OPERATION_LABELS,
  ...Object.entries(EXTRA_OPERATION_LABELS),
]);

export const AUDIT_OPERATION_OPTIONS = [...OPERATION_LABEL_VALUES.entries()]
  .map(([value, label]) => ({ value, label }))
  .sort((left, right) => left.label.localeCompare(right.label, "zh-CN"));

export const AUDIT_RESULT_OPTIONS = [
  { value: "SUCCESS", label: "成功" },
  { value: "FAILURE", label: "失败" },
  { value: "DENIED", label: "拒绝" },
] as const;
