import { z } from "zod";

import { assetUrl } from "@/lib/asset-url";

export type ParameterGroupKey =
  "site" | "session" | "captcha" | "mail" | "audit" | "server" | "extension-resources";

type ParameterFieldKind =
  | "text"
  | "textarea"
  | "url"
  | "email"
  | "password"
  | "number"
  | "boolean"
  | "select"
  | "user-group"
  | "readonly"
  | "json";

type ParameterFieldOption = {
  value: string;
  label: string;
};

export type ParameterField = {
  name: string;
  path?: readonly string[];
  label: string;
  hint?: string;
  kind: ParameterFieldKind;
  section?: string;
  defaultValue?: string;
  previewFallback?: string;
  width?: "sm" | "md";
  required?: boolean;
  maxLength?: number;
  min?: number;
  max?: number;
  rows?: number;
  options?: readonly ParameterFieldOption[];
  readOnly?: boolean;
  jsonShape?: "object" | "array";
};

export type ParameterGroup = {
  key: ParameterGroupKey;
  label: string;
  description: string;
  fields: readonly ParameterField[];
};

export type ParameterFieldSet = {
  fields: readonly ParameterField[];
};

export type ParameterFieldSection = {
  title?: string;
  fields: ParameterField[];
};

export function groupParameterFieldsBySection(
  fields: readonly ParameterField[],
): ParameterFieldSection[] {
  return fields.reduce<ParameterFieldSection[]>((sections, field) => {
    const current = sections[sections.length - 1];

    if (current && current.title === field.section) {
      current.fields.push(field);
    } else {
      sections.push({ title: field.section, fields: [field] });
    }

    return sections;
  }, []);
}

export type ParameterFormValues = Record<string, string | number | boolean>;

const CAPTCHA_PROVIDERS = [
  { value: "image", label: "图形" },
  { value: "none", label: "关闭验证" },
] as const;

const DEFAULT_LOGO_DARK = assetUrl("brand/logo-dark.svg");
const DEFAULT_LOGO_LIGHT = assetUrl("brand/logo-light.svg");
const DEFAULT_FAVICON = assetUrl("brand/icon-small.svg");
const DEFAULT_ICON_MEDIUM = assetUrl("brand/icon-medium.svg");
const DEFAULT_ICON_LARGE = assetUrl("brand/icon-large.svg");

export const PARAMETER_GROUPS: readonly ParameterGroup[] = [
  {
    key: "site",
    label: "站点",
    description: "站点对外展示的基本信息与 Logo 资源。",
    fields: [
      {
        name: "siteName",
        label: "站点名称",
        kind: "text",
        section: "基本信息",
        required: true,
        maxLength: 80,
        hint: "站点的名称。",
      },
      {
        name: "siteDescription",
        label: "站点描述",
        kind: "textarea",
        section: "基本信息",
        maxLength: 300,
        rows: 3,
        hint: "站点描述信息，可能会在分享页面摘要内展示。",
      },
      {
        name: "siteUrl",
        label: "站点 URL",
        kind: "url",
        section: "基本信息",
        maxLength: 2048,
        hint: "主要站点 URL 用于与外部服务通信和接收回调，请使用能被公网访问的 URL。",
      },
      {
        name: "footerCode",
        label: "页脚代码",
        kind: "textarea",
        section: "基本信息",
        maxLength: 20000,
        rows: 6,
        hint: "在页面底部插入的自定义 HTML 代码。",
      },
      {
        name: "siteAnnouncement",
        label: "站点公告",
        kind: "textarea",
        section: "基本信息",
        maxLength: 2000,
        rows: 4,
        hint: "展示给已登录用户的公告，留空不展示。",
      },
      {
        name: "logoDark",
        label: "黑暗模式 Logo",
        kind: "url",
        section: "Logo",
        previewFallback: DEFAULT_LOGO_DARK,
        maxLength: 2048,
        hint: "黑暗模式下展示的 Logo 地址。",
      },
      {
        name: "logoLight",
        label: "日间模式 Logo",
        kind: "url",
        section: "Logo",
        previewFallback: DEFAULT_LOGO_LIGHT,
        maxLength: 2048,
        hint: "日间模式下展示的 Logo 地址。",
      },
      {
        name: "favicon",
        label: "小图标",
        kind: "url",
        section: "Logo",
        previewFallback: DEFAULT_FAVICON,
        maxLength: 2048,
        hint: "小图标地址，ico 或 svg 格式；也会用于浏览器标签、书签等位置。",
      },
      {
        name: "iconMedium",
        label: "中图标",
        kind: "url",
        section: "Logo",
        previewFallback: DEFAULT_ICON_MEDIUM,
        maxLength: 2048,
        hint: "192x192 的中图标地址，png 格式。",
      },
      {
        name: "iconLarge",
        label: "大图标",
        kind: "url",
        section: "Logo",
        previewFallback: DEFAULT_ICON_LARGE,
        maxLength: 2048,
        hint: "512x512 的大图标地址，png 格式；也会用于 iOS 客户端切换站点。",
      },
    ],
  },
  {
    key: "session",
    label: "会话",
    description: "登录方式、登录安全、邮箱可见性与头像存储参数。",
    fields: [
      {
        name: "passkeyLoginEnabled",
        label: "通行密钥登录",
        kind: "boolean",
        section: "登录",
        hint: "允许用户使用绑定的人脸、指纹或 USB 安全密钥登录；站点需要启用 HTTPS。",
      },
      {
        name: "exposeEmailToSignedInUsers",
        label: "向已登录用户公开邮箱",
        kind: "boolean",
        section: "登录",
        hint: "启用后，已登录用户可在共享页面等位置看到其他用户的邮箱。",
      },
      {
        name: "defaultUserGroup",
        label: "默认用户组",
        kind: "user-group",
        section: "登录",
        width: "md",
        hint: "新用户加入时的初始用户组。",
      },
      {
        name: "avatarStoragePath",
        label: "头像存储路径",
        kind: "text",
        section: "头像",
        maxLength: 255,
        hint: "相对于数据目录的头像存储路径。",
      },
      {
        name: "avatarMaxSize",
        label: "头像大小上限",
        kind: "number",
        section: "头像",
        min: 1,
        max: 52_428_800,
        hint: "单位：字节。",
      },
      {
        name: "avatarDimension",
        label: "图像尺寸 (px)",
        kind: "number",
        section: "头像",
        min: 16,
        max: 2048,
        hint: "上传头像会被调整到给定尺寸，单位为像素。",
      },
      {
        name: "rsaLoginEncryptionEnabled",
        label: "RSA 加密传输",
        kind: "boolean",
        section: "登录安全",
        defaultValue: "true",
        hint: "登录密码在浏览器端使用 RSA 公钥加密后提交；关闭后密码将以明文提交，请确保站点已启用 HTTPS。",
      },
      {
        name: "argon2PasswordStorageEnabled",
        label: "Argon2 存储",
        kind: "boolean",
        section: "登录安全",
        defaultValue: "true",
        hint: "开启后新密码使用 Argon2 编码；关闭后新密码使用 BCrypt，已有密码仍可正常验证。",
      },
    ],
  },
  {
    key: "captcha",
    label: "验证码",
    description: "验证码的启用范围与图形策略。",
    fields: [
      {
        name: "loginCaptchaEnabled",
        label: "登录验证码",
        kind: "boolean",
        section: "启用范围",
        hint: "是否启用登录表单验证码。",
      },
      {
        name: "recoveryCaptchaEnabled",
        label: "找回密码验证码",
        kind: "boolean",
        section: "启用范围",
        hint: "是否启用找回密码表单验证码。",
      },
      {
        name: "captchaProvider",
        label: "验证码类型",
        kind: "select",
        section: "验证码类型",
        width: "md",
        options: CAPTCHA_PROVIDERS,
        hint: "选择验证码类型和验证码服务提供商。",
      },
      {
        name: "captchaMode",
        label: "模式",
        kind: "select",
        section: "验证码类型",
        width: "sm",
        options: [
          { value: "TEXT", label: "数字+字母" },
          { value: "MATH", label: "算术" },
        ],
      },
      {
        name: "captchaLength",
        label: "长度",
        kind: "number",
        section: "验证码类型",
        width: "sm",
        min: 3,
        max: 8,
        hint: "验证码中字符的长度。",
      },
      {
        name: "captchaInterferenceText",
        label: "加强干扰文字",
        kind: "boolean",
        section: "验证码类型",
      },
      {
        name: "captchaInterferenceDots",
        label: "加强干扰点",
        kind: "boolean",
        section: "验证码类型",
      },
      {
        name: "captchaUseHollowLine",
        label: "使用空心线",
        kind: "boolean",
        section: "验证码类型",
      },
      {
        name: "captchaUseNoise",
        label: "使用噪点",
        kind: "boolean",
        section: "验证码类型",
      },
      {
        name: "captchaUseInterferenceText",
        label: "使用干扰文字",
        kind: "boolean",
        section: "验证码类型",
      },
      {
        name: "captchaUseWaveLine",
        label: "使用波浪线",
        kind: "boolean",
        section: "验证码类型",
      },
      {
        name: "captchaUseSineLine",
        label: "使用正弦线",
        kind: "boolean",
        section: "验证码类型",
      },
    ],
  },
  {
    key: "mail",
    label: "邮件",
    description: "SMTP 发件配置与邮件投递参数。",
    fields: [
      {
        name: "mailFromName",
        label: "发件人名",
        kind: "text",
        section: "发信",
        maxLength: 120,
        hint: "邮件中展示的发件人姓名。",
      },
      {
        name: "mailFromAddress",
        label: "发件人邮箱",
        kind: "email",
        section: "发信",
        maxLength: 254,
        hint: "发件邮箱的地址。",
      },
      {
        name: "smtpHost",
        label: "SMTP 服务器",
        kind: "text",
        section: "发信",
        maxLength: 255,
        hint: "发件服务器地址，不含端口号。",
      },
      {
        name: "smtpPort",
        label: "SMTP 端口",
        kind: "number",
        section: "发信",
        min: 1,
        max: 65_535,
        hint: "发件服务器端口号。",
      },
      {
        name: "smtpUsername",
        label: "SMTP 用户名",
        kind: "text",
        section: "发信",
        maxLength: 255,
        hint: "发信邮箱用户名，一般与邮箱地址相同。",
      },
      {
        name: "smtpPassword",
        label: "SMTP 密码",
        kind: "password",
        section: "发信",
        maxLength: 512,
        hint: "发信邮箱密码。",
      },
      {
        name: "mailReplyTo",
        label: "回信邮箱",
        kind: "email",
        section: "发信",
        maxLength: 254,
        hint: "用户回复系统发送的邮件时，用于接收回信的邮箱。",
      },
      {
        name: "smtpForceSsl",
        label: "强制使用 SSL 连接",
        kind: "boolean",
        section: "发信",
        hint: "是否强制使用 SSL 加密连接。如果无法发送邮件，可关闭此项后重试。",
      },
      {
        name: "smtpConnectionTtlSeconds",
        label: "SMTP 连接有效期（秒）",
        kind: "number",
        section: "发信",
        min: 0,
        max: 86_400,
        hint: "有效期内建立的 SMTP 连接会被新邮件发送请求复用。",
      },
      {
        name: "passwordResetTemplate",
        path: ["mailTemplates", "passwordReset"],
        label: "密码重置模板",
        kind: "textarea",
        section: "邮件模板",
        rows: 12,
        hint: "留空时使用系统默认模板；支持 {{siteName}}、{{siteUrl}}、{{username}}、{{code}}、{{ttlMinutes}} 等变量。",
      },
    ],
  },
  {
    key: "audit",
    label: "审计",
    description: "配置需要写入审计日志的操作事件与资源类型。",
    fields: [],
  },
  {
    key: "server",
    label: "服务器设置",
    description: "临时路径、站点标识、密钥、令牌有效期与垃圾回收参数。",
    fields: [],
  },
  {
    key: "extension-resources",
    label: "扩展资源",
    description:
      "管理 Webhook、ConfigMap、Secret、Setting、RememberMeToken 与 AnnotationSetting 扩展资源。",
    fields: [],
  },
];

export function getParameterGroup(key: ParameterGroupKey): ParameterGroup {
  const group = PARAMETER_GROUPS.find((item) => item.key === key);

  if (!group) {
    throw new Error(`未找到参数分组：${key}`);
  }

  return group;
}

export function normalizeParameterGroupKey(value: unknown): ParameterGroupKey {
  return PARAMETER_GROUPS.some((group) => group.key === value)
    ? (value as ParameterGroupKey)
    : "site";
}

export function createParameterFormValues(
  group: ParameterFieldSet,
  source: Record<string, unknown>,
): ParameterFormValues {
  const values: ParameterFormValues = {};

  for (const field of group.fields) {
    const value = readPath(source, field.path ?? [field.name]);

    if (field.kind === "boolean") {
      values[field.name] = typeof value === "boolean" ? value : field.defaultValue === "true";
    } else if (field.kind === "number") {
      values[field.name] = typeof value === "number" ? value : Number(field.defaultValue ?? 0);
    } else if (field.kind === "json") {
      values[field.name] = formatJsonText(value ?? field.defaultValue);
    } else if (field.kind === "select") {
      values[field.name] =
        typeof value === "string" && value ? value : (field.options?.[0]?.value ?? "");
    } else {
      const stringValue = value == null ? "" : String(value);
      values[field.name] = stringValue || field.defaultValue || "";
    }
  }

  return values;
}

export function createParameterRequestBody(
  group: ParameterFieldSet,
  values: ParameterFormValues,
): Record<string, unknown> {
  const body: Record<string, unknown> = {};

  for (const field of group.fields) {
    const value = values[field.name];
    let normalized: unknown = value;

    if (field.kind === "number") {
      normalized = Number(value);
    } else if (field.kind === "json") {
      normalized = parseParameterJson(String(value ?? ""));
    }

    setPath(body, field.path ?? [field.name], normalized);
  }

  return body;
}

export function createParameterSchema(
  group: ParameterFieldSet,
): z.ZodType<ParameterFormValues, ParameterFormValues> {
  const shape: Record<string, z.ZodType> = {};

  for (const field of group.fields) {
    shape[field.name] = createFieldSchema(field);
  }

  return z.object(shape) as unknown as z.ZodType<ParameterFormValues, ParameterFormValues>;
}

function createFieldSchema(field: ParameterField): z.ZodType {
  if (field.kind === "boolean") {
    return z.boolean();
  }

  if (field.kind === "number") {
    let schema = z.number({ error: `请输入有效的${field.label}。` });

    if (field.min !== undefined) {
      schema = schema.min(field.min, `${field.label}不能小于 ${field.min}。`);
    }
    if (field.max !== undefined) {
      schema = schema.max(field.max, `${field.label}不能大于 ${field.max}。`);
    }

    return schema;
  }

  if (field.kind === "select" || field.kind === "user-group") {
    return z.string().min(1, `请选择${field.label}。`);
  }

  if (field.kind === "json") {
    return z.string().superRefine((value, context) => {
      const text = value.trim();

      if (!text) {
        if (field.required) {
          context.addIssue({ code: "custom", message: `请输入${field.label}。` });
        }
        return;
      }

      let parsed: unknown;
      try {
        parsed = JSON.parse(text) as unknown;
      } catch {
        context.addIssue({ code: "custom", message: `${field.label}必须是有效的 JSON。` });
        return;
      }

      const isObject = parsed !== null && typeof parsed === "object" && !Array.isArray(parsed);
      if (field.jsonShape === "object" && !isObject) {
        context.addIssue({ code: "custom", message: `${field.label}必须是 JSON 对象。` });
      }
      if (field.jsonShape === "array" && !Array.isArray(parsed)) {
        context.addIssue({ code: "custom", message: `${field.label}必须是 JSON 数组。` });
      }
    });
  }

  let schema = z.string().trim();

  if (field.required) {
    schema = schema.min(1, `请输入${field.label}。`);
  }
  if (field.maxLength !== undefined) {
    schema = schema.max(field.maxLength, `${field.label}不能超过 ${field.maxLength} 个字符。`);
  }
  if (field.kind === "url") {
    schema = schema.refine(
      (value) => value === "" || z.url().safeParse(value).success,
      `请输入有效的${field.label}。`,
    );
  }
  if (field.kind === "email") {
    schema = schema.refine(
      (value) => value === "" || z.email().safeParse(value).success,
      `请输入有效的${field.label}。`,
    );
  }

  return schema;
}

export function parseParameterJson<T = unknown>(text: string): T | undefined {
  const trimmed = text.trim();

  if (!trimmed) {
    return undefined;
  }

  try {
    return JSON.parse(trimmed) as T;
  } catch {
    return undefined;
  }
}

function formatJsonText(value: unknown): string {
  if (value === undefined || value === null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value, null, 2);
}

function readPath(source: Record<string, unknown>, path: readonly string[]): unknown {
  return path.reduce<unknown>((value, key) => {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      return undefined;
    }
    return (value as Record<string, unknown>)[key];
  }, source);
}

function setPath(target: Record<string, unknown>, path: readonly string[], value: unknown): void {
  let cursor = target;

  path.forEach((key, index) => {
    if (index === path.length - 1) {
      cursor[key] = value;
      return;
    }

    const next = cursor[key];

    if (!next || typeof next !== "object" || Array.isArray(next)) {
      const object: Record<string, unknown> = {};
      cursor[key] = object;
      cursor = object;
      return;
    }

    cursor = next as Record<string, unknown>;
  });
}
