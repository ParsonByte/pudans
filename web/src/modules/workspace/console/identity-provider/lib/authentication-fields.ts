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
