export type SystemConfigGroup = {
  value: string;
  label: string;
  description: string;
};

export const SYSTEM_CONFIG_GROUPS: readonly SystemConfigGroup[] = [
  {
    value: "site",
    label: "站点",
    description: "站点名称、地址、Logo 与公告。",
  },
  {
    value: "session",
    label: "会话",
    description: "登录方式、默认用户组与头像参数。",
  },
  {
    value: "authentication",
    label: "身份认证",
    description: "TOTP、OAuth2、OIDC、飞书与个人访问令牌。",
  },
  {
    value: "captcha",
    label: "验证码",
    description: "登录与找回密码的验证码策略。",
  },
  {
    value: "mail",
    label: "邮件",
    description: "SMTP 发件配置与邮件模板。",
  },
  {
    value: "audit",
    label: "审计",
    description: "审计记录规则与资源类型。",
  },
  {
    value: "server",
    label: "服务器",
    description: "临时路径、站点标识、令牌与垃圾回收参数。",
  },
  {
    value: "storage",
    label: "存储",
    description: "存储策略与上传限制参数。",
  },
  {
    value: "basic",
    label: "旧版站点（basic）",
    description: "兼容旧版 basic 系统配置分组。",
  },
  {
    value: "user",
    label: "旧版用户（user）",
    description: "兼容旧版 user 系统配置分组。",
  },
  {
    value: "seo",
    label: "旧版 SEO（seo）",
    description: "兼容旧版 seo 系统配置分组。",
  },
  {
    value: "authProvider",
    label: "旧版登录提供方（authProvider）",
    description: "兼容旧版 authProvider 登录提供方状态分组。",
  },
];

export const DEFAULT_SYSTEM_CONFIG_GROUP = SYSTEM_CONFIG_GROUPS[0].value;
