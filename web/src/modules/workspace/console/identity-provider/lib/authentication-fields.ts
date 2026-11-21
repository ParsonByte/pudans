import type { ParameterFieldSet } from "@/modules/workspace/console/settings/lib/parameter-fields";

export const AUTHENTICATION_GROUP = "authentication";

export type ProviderKind = "oauth2" | "oidc";

export type ProviderValues = {
  registrationId: string;
  displayName: string;
  logo: string;
  clientId: string;
  clientSecret: string;
  issuerUri: string;
  authorizationUri: string;
  tokenUri: string;
  userInfoUri: string;
  userNameAttribute: string;
  scopes: string;
  enabled: boolean;
};

export const TOTP_FIELDS: ParameterFieldSet = {
  fields: [
    {
      name: "enabled",
      path: ["totp", "enabled"],
      label: "启用 TOTP 两步验证",
      kind: "boolean",
      defaultValue: "true",
      hint: "关闭后，已配置 TOTP 的账号在登录时不再要求二次验证。",
    },
    {
      name: "issuer",
      path: ["totp", "issuer"],
      label: "签发方名称",
      kind: "text",
      maxLength: 128,
      hint: "认证器应用中展示的签发方名称，留空时使用 pudans。",
    },
  ],
};

export const FEISHU_FIELDS: ParameterFieldSet = {
  fields: [
    {
      name: "enabled",
      path: ["feishu", "enabled"],
      label: "启用飞书登录",
      kind: "boolean",
      section: "飞书应用",
      defaultValue: "true",
      hint: "保存后会覆盖环境变量中的飞书配置；未在此保存时沿用服务端配置。",
    },
    {
      name: "appId",
      path: ["feishu", "appId"],
      label: "App ID",
      kind: "text",
      section: "飞书应用",
      maxLength: 128,
      hint: "飞书开放平台应用的 App ID；留空时沿用服务端配置。",
    },
    {
      name: "appSecret",
      path: ["feishu", "appSecret"],
      label: "App Secret",
      kind: "password",
      section: "飞书应用",
      maxLength: 512,
      hint: "留空或保持 ******** 表示沿用已保存的密钥。",
    },
    {
      name: "redirectUri",
      path: ["feishu", "redirectUri"],
      label: "回调地址",
      kind: "text",
      section: "飞书应用",
      defaultValue: "{baseUrl}/apis/api.pudans.tolim.cn/v1alpha1/oauth2/callback/feishu",
      maxLength: 2048,
      hint: "支持 {baseUrl} 占位符，需与飞书开放平台配置的回调地址一致。",
    },
    {
      name: "authorizationUri",
      path: ["feishu", "authorizationUri"],
      label: "授权地址",
      kind: "url",
      section: "飞书应用",
      defaultValue: "https://open.feishu.cn/open-apis/authen/v1/authorize",
      maxLength: 2048,
    },
    {
      name: "scope",
      path: ["feishu", "scope"],
      label: "权限范围",
      kind: "text",
      section: "飞书应用",
      defaultValue: "contact:user.base:readonly contact:user.email:readonly",
      maxLength: 1024,
      hint: "以空格分隔的飞书权限范围。",
    },
    {
      name: "syncIntervalSeconds",
      path: ["feishu", "syncIntervalSeconds"],
      label: "资料同步间隔（秒）",
      kind: "number",
      section: "同步",
      defaultValue: "21600",
      min: 0,
      max: 2_592_000,
      hint: "0 表示关闭定时同步。",
    },
    {
      name: "alertChatId",
      path: ["feishu", "alertChatId"],
      label: "告警群 Chat ID",
      kind: "text",
      section: "同步",
      maxLength: 128,
      hint: "用于接收安全告警的飞书群 ID，留空表示不发送。",
    },
  ],
};

export const PAT_FIELDS: ParameterFieldSet = {
  fields: [
    {
      name: "enabled",
      path: ["pat", "enabled"],
      label: "允许创建个人访问令牌",
      kind: "boolean",
      defaultValue: "true",
      hint: "关闭后用户无法创建新的 PAT，已有令牌继续有效。",
    },
    {
      name: "maxLifetimeDays",
      path: ["pat", "maxLifetimeDays"],
      label: "最长有效期（天）",
      kind: "number",
      min: 0,
      max: 3650,
      hint: "0 表示不限制；用户创建 PAT 时必须选择不超过该天数的过期时间。",
    },
  ],
};

export function authProviderFields(editing: boolean): ParameterFieldSet {
  return {
    fields: [
      {
        name: "metadata.name",
        path: ["metadata", "name"],
        label: "名称",
        kind: "text",
        section: "基本信息",
        required: true,
        maxLength: 63,
        readOnly: editing,
        hint: editing ? "提供方名称创建后不可修改。" : "小写字母、数字、- 和 .，创建后不可修改。",
      },
      {
        name: "spec.displayName",
        path: ["spec", "displayName"],
        label: "显示名称",
        kind: "text",
        section: "基本信息",
        required: true,
        maxLength: 128,
        hint: "登录页展示的提供方名称。",
      },
      {
        name: "spec.description",
        path: ["spec", "description"],
        label: "描述",
        kind: "textarea",
        section: "基本信息",
        rows: 3,
        maxLength: 500,
      },
      {
        name: "spec.authType",
        path: ["spec", "authType"],
        label: "认证类型",
        kind: "select",
        section: "基本信息",
        options: [
          { value: "FORM", label: "表单" },
          { value: "OAUTH2", label: "OAuth2" },
        ],
        defaultValue: "OAUTH2",
      },
      {
        name: "spec.authenticationUrl",
        path: ["spec", "authenticationUrl"],
        label: "登录地址",
        kind: "text",
        section: "登录入口",
        required: true,
        maxLength: 2048,
        hint: "相对路径或完整地址，例如 /login。",
      },
      {
        name: "spec.method",
        path: ["spec", "method"],
        label: "登录方法",
        kind: "select",
        section: "登录入口",
        options: [
          { value: "GET", label: "GET" },
          { value: "POST", label: "POST" },
        ],
        defaultValue: "GET",
      },
      {
        name: "spec.rememberMeSupport",
        path: ["spec", "rememberMeSupport"],
        label: "支持记住登录",
        kind: "boolean",
        section: "登录入口",
      },
      {
        name: "spec.logo",
        path: ["spec", "logo"],
        label: "Logo 地址",
        kind: "url",
        section: "展示",
        maxLength: 2048,
      },
      {
        name: "spec.website",
        path: ["spec", "website"],
        label: "官网地址",
        kind: "url",
        section: "展示",
        maxLength: 2048,
      },
      {
        name: "spec.helpPage",
        path: ["spec", "helpPage"],
        label: "帮助页面",
        kind: "text",
        section: "展示",
        maxLength: 2048,
        hint: "相对路径或完整地址。",
      },
      {
        name: "spec.bindingUrl",
        path: ["spec", "bindingUrl"],
        label: "绑定地址",
        kind: "text",
        section: "绑定",
        maxLength: 2048,
        hint: "可选，账号绑定入口地址。",
      },
      {
        name: "spec.unbindUrl",
        path: ["spec", "unbindUrl"],
        label: "解绑地址",
        kind: "text",
        section: "绑定",
        maxLength: 2048,
        hint: "可选，账号解绑入口地址。",
      },
      {
        name: "spec.settingRef.name",
        path: ["spec", "settingRef", "name"],
        label: "设置名称",
        kind: "text",
        section: "引用",
        maxLength: 63,
        hint: "可选，关联的 Setting 资源名称。",
      },
      {
        name: "spec.settingRef.group",
        path: ["spec", "settingRef", "group"],
        label: "设置分组",
        kind: "text",
        section: "引用",
        maxLength: 63,
        hint: "填写设置名称时必填，与 Setting 中的表单分组一致。",
      },
      {
        name: "spec.configMapRef.name",
        path: ["spec", "configMapRef", "name"],
        label: "配置名称",
        kind: "text",
        section: "引用",
        maxLength: 63,
        hint: "可选，关联的 ConfigMap 资源名称。",
      },
    ],
  };
}

export const emptyProvider = (kind: ProviderKind): ProviderValues => ({
  registrationId: "",
  displayName: "",
  logo: "",
  clientId: "",
  clientSecret: "",
  issuerUri: "",
  authorizationUri: "",
  tokenUri: "",
  userInfoUri: "",
  userNameAttribute: kind === "oidc" ? "sub" : "id",
  scopes: "",
  enabled: true,
});

export function readProviders(data: Record<string, unknown>, kind: ProviderKind): ProviderValues[] {
  const group = data[kind];
  if (!group || typeof group !== "object") {
    return [];
  }
  const providers = (group as { providers?: unknown }).providers;
  if (!Array.isArray(providers)) {
    return [];
  }
  return providers.filter(isRecord).map((provider) => ({
    registrationId: stringValue(provider.registrationId),
    displayName: stringValue(provider.displayName),
    logo: stringValue(provider.logo),
    clientId: stringValue(provider.clientId),
    clientSecret: stringValue(provider.clientSecret),
    issuerUri: stringValue(provider.issuerUri),
    authorizationUri: stringValue(provider.authorizationUri),
    tokenUri: stringValue(provider.tokenUri),
    userInfoUri: stringValue(provider.userInfoUri),
    userNameAttribute: stringValue(provider.userNameAttribute),
    scopes: stringValue(provider.scopes),
    enabled: provider.enabled !== false,
  }));
}

export function readObject(data: Record<string, unknown>, key: string): Record<string, unknown> {
  const value = data[key];
  return isRecord(value) ? value : {};
}

export function readBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === "boolean" ? value : fallback;
}

export function readNumber(value: unknown, fallback: number): number {
  return typeof value === "number" ? value : fallback;
}

export function readString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function stringValue(value: unknown): string {
  return readString(value);
}
