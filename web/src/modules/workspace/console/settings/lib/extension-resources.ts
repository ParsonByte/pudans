import {
  type AnnotationSetting,
  type ConfigMap,
  type RememberMeToken,
  type Secret,
  type Setting,
  type Webhook,
  apiClient,
} from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";

import {
  type ExtensionRecord,
  asRecord,
  isImmutableExtension,
  mergeExtensionRecord,
  normalizeExtensionRecord,
  splitList,
  toStringMap,
} from "./extension-records";
import {
  type ParameterField,
  type ParameterFieldSet,
  type ParameterFormValues,
  parseParameterJson,
} from "./parameter-fields";

export type ExtensionResourceKind =
  "webhooks" | "configmaps" | "secrets" | "settings" | "remembermetokens" | "annotationsettings";

export type ExtensionResourceDefinition = {
  plural: ExtensionResourceKind;
  kindName: string;
  apiVersion: string;
  label: string;
  description: string;
  dialogDescription: string;
};

export type ExtensionResourceSummary = {
  summary: string;
  detail: string;
  badges: string[];
};

const LIST_SIZE = 200;

const PROTECTED_CONFIGMAP_NAMES = new Set([
  "parameter-settings",
  "parameter-settings-default",
  "system",
  "system-default",
]);

export const EXTENSION_RESOURCES: readonly ExtensionResourceDefinition[] = [
  {
    plural: "webhooks",
    kindName: "Webhook",
    apiVersion: "pudans.tolim.cn/v1alpha1",
    label: "回调 Webhook",
    description: "向外部系统推送审计与业务事件。",
    dialogDescription: "签名密钥不会回显；保存时留空会清除已保存的密钥。",
  },
  {
    plural: "configmaps",
    kindName: "ConfigMap",
    apiVersion: "v1alpha1",
    label: "配置 ConfigMap",
    description: "保存非敏感的分组配置数据。",
    dialogDescription: "参数设置使用的 ConfigMap 由系统维护，不能删除。",
  },
  {
    plural: "secrets",
    kindName: "Secret",
    apiVersion: "v1alpha1",
    label: "密钥 Secret",
    description: "保存需要保护的键值数据，列表不会展示具体值。",
    dialogDescription: "已保存的值不会回显，输入新值即可替换，留空表示保持不变。",
  },
  {
    plural: "settings",
    kindName: "Setting",
    apiVersion: "v1alpha1",
    label: "设置表单 Setting",
    description: "定义供账号资料或扩展资源使用的表单结构。",
    dialogDescription: "表单定义是 JSON 数组，每项包含 group、label 与 formSchema。",
  },
  {
    plural: "remembermetokens",
    kindName: "RememberMeToken",
    apiVersion: "security.pudans.tolim.cn/v1alpha1",
    label: "记住登录令牌",
    description: "查看与撤销持久登录令牌。",
    dialogDescription: "令牌序列与令牌值不会回显；更新时必须重新填写完整凭据。",
  },
  {
    plural: "annotationsettings",
    kindName: "AnnotationSetting",
    apiVersion: "v1alpha1",
    label: "注解设置 AnnotationSetting",
    description: "按目标类型定义注解表单结构。",
    dialogDescription: "注解设置按目标引用与表单 JSON 定义。",
  },
];

export function extensionResourceDefinition(
  kind: ExtensionResourceKind,
): ExtensionResourceDefinition {
  const definition = EXTENSION_RESOURCES.find((item) => item.plural === kind);

  if (!definition) {
    throw new Error(`未知的扩展资源类型：${kind}`);
  }

  return definition;
}

export async function listExtensionResources(
  kind: ExtensionResourceKind,
  targetRef = "",
): Promise<ExtensionRecord[]> {
  switch (kind) {
    case "webhooks": {
      const response = await apiClient.webhook.listWebhook({ page: 1, size: LIST_SIZE });
      return (response.data.items ?? []).map(toRecord);
    }
    case "configmaps": {
      const response = await apiClient.configMap.listConfigMap({ page: 1, size: LIST_SIZE });
      return (response.data.items ?? []).map(toRecord);
    }
    case "secrets": {
      const response = await apiClient.secret.listSecret({ page: 1, size: LIST_SIZE });
      return (response.data.items ?? []).map(toRecord);
    }
    case "settings": {
      const response = await apiClient.setting.listSetting({ page: 1, size: LIST_SIZE });
      return (response.data.items ?? []).map(toRecord);
    }
    case "remembermetokens": {
      const response = await apiClient.rememberMeToken.listRememberMeToken({
        page: 1,
        size: LIST_SIZE,
      });
      return (response.data.items ?? []).map(toRecord);
    }
    case "annotationsettings": {
      const response = await apiClient.annotationSetting.listAvailableAnnotationSettings({
        targetRef,
        page: 1,
        size: LIST_SIZE,
      });
      return (response.data.items ?? []).map(toRecord);
    }
  }
}

export async function createExtensionResource(
  kind: ExtensionResourceKind,
  record: ExtensionRecord,
): Promise<void> {
  switch (kind) {
    case "webhooks":
      await apiClient.webhook.createWebhook({ webhook: record as unknown as Webhook });
      break;
    case "configmaps":
      await apiClient.configMap.createConfigMap({ configMap: record as unknown as ConfigMap });
      break;
    case "secrets":
      await apiClient.secret.createSecret({ secret: record as unknown as Secret });
      break;
    case "settings":
      await apiClient.setting.createSetting({ setting: record as unknown as Setting });
      break;
    case "remembermetokens":
      await apiClient.rememberMeToken.createRememberMeToken({
        rememberMeToken: record as unknown as RememberMeToken,
      });
      break;
    case "annotationsettings":
      await apiClient.annotationSetting.createAnnotationSetting({
        annotationSetting: record as unknown as AnnotationSetting,
      });
      break;
  }
}

export async function updateExtensionResource(
  kind: ExtensionResourceKind,
  name: string,
  record: ExtensionRecord,
): Promise<void> {
  switch (kind) {
    case "webhooks":
      await apiClient.webhook.updateWebhook({ name, webhook: record as unknown as Webhook });
      break;
    case "configmaps":
      await apiClient.configMap.updateConfigMap({
        name,
        configMap: record as unknown as ConfigMap,
      });
      break;
    case "secrets":
      await apiClient.secret.updateSecret({ name, secret: record as unknown as Secret });
      break;
    case "settings":
      await apiClient.setting.updateSetting({ name, setting: record as unknown as Setting });
      break;
    case "remembermetokens":
      await apiClient.rememberMeToken.updateRememberMeToken({
        name,
        rememberMeToken: record as unknown as RememberMeToken,
      });
      break;
    case "annotationsettings":
      await apiClient.annotationSetting.updateAnnotationSetting({
        name,
        annotationSetting: record as unknown as AnnotationSetting,
      });
      break;
  }
}

export async function deleteExtensionResource(
  kind: ExtensionResourceKind,
  name: string,
): Promise<void> {
  switch (kind) {
    case "webhooks":
      await apiClient.webhook.deleteWebhook({ name });
      break;
    case "configmaps":
      await apiClient.configMap.deleteConfigMap({ name });
      break;
    case "secrets":
      await apiClient.secret.deleteSecret({ name });
      break;
    case "settings":
      await apiClient.setting.deleteSetting({ name });
      break;
    case "remembermetokens":
      await apiClient.rememberMeToken.deleteRememberMeToken({ name });
      break;
    case "annotationsettings":
      await apiClient.annotationSetting.deleteAnnotationSetting({ name });
      break;
  }
}

export function resolveExtensionResourceError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "资源不存在或已被删除。",
    conflict: "资源名称已存在，或与现有资源冲突。",
  });
}

export function isDeletableExtensionResource(
  kind: ExtensionResourceKind,
  record: ExtensionRecord,
): boolean {
  if (isImmutableExtension(record)) {
    return false;
  }
  return !(kind === "configmaps" && PROTECTED_CONFIGMAP_NAMES.has(record.metadata.name));
}

export function extensionResourceFields(
  kind: ExtensionResourceKind,
  editing: boolean,
): ParameterFieldSet {
  switch (kind) {
    case "webhooks":
      return {
        fields: [
          nameField(editing),
          {
            name: "spec.url",
            path: ["spec", "url"],
            label: "回调地址",
            kind: "url",
            section: "投递",
            required: true,
            maxLength: 2048,
          },
          {
            name: "spec.secret",
            path: ["spec", "secret"],
            label: "签名密钥",
            kind: "password",
            section: "投递",
            maxLength: 2048,
            hint: "密钥不会回显；保存时留空会清除已保存的密钥。",
          },
          {
            name: "spec.events",
            path: ["spec", "events"],
            label: "订阅事件",
            kind: "text",
            section: "投递",
            maxLength: 2048,
            hint: "以逗号或空格分隔；* 表示全部事件。",
          },
          {
            name: "spec.description",
            path: ["spec", "description"],
            label: "描述",
            kind: "textarea",
            section: "投递",
            rows: 3,
            maxLength: 500,
          },
          {
            name: "spec.enabled",
            path: ["spec", "enabled"],
            label: "启用回调",
            kind: "boolean",
            section: "投递",
            defaultValue: "true",
          },
          {
            name: "spec.maxAttempts",
            path: ["spec", "maxAttempts"],
            label: "最大重试次数",
            kind: "number",
            section: "重试",
            min: 1,
            max: 10,
            defaultValue: "3",
          },
          {
            name: "spec.timeoutMillis",
            path: ["spec", "timeoutMillis"],
            label: "请求超时（毫秒）",
            kind: "number",
            section: "重试",
            min: 1000,
            max: 60000,
            defaultValue: "5000",
          },
          {
            name: "spec.headers",
            path: ["spec", "headers"],
            label: "附加请求头",
            kind: "json",
            section: "重试",
            jsonShape: "object",
            rows: 4,
            hint: "JSON 对象，键和值均会按字符串发送。",
          },
        ],
      };
    case "configmaps":
      return {
        fields: [
          nameField(editing),
          {
            name: "data",
            path: ["data"],
            label: "配置数据",
            kind: "json",
            jsonShape: "object",
            rows: 12,
            defaultValue: "{}",
            hint: "JSON 对象；值会作为字符串保存。",
          },
        ],
      };
    case "settings":
      return {
        fields: [
          nameField(editing),
          {
            name: "spec.forms",
            path: ["spec", "forms"],
            label: "表单定义",
            kind: "json",
            jsonShape: "array",
            required: true,
            rows: 14,
            defaultValue: "[]",
            hint: "JSON 数组；每项包含 group、label 与 formSchema。",
          },
        ],
      };
    case "remembermetokens":
      return {
        fields: [
          nameField(editing),
          {
            name: "spec.username",
            path: ["spec", "username"],
            label: "用户名",
            kind: "text",
            required: true,
            maxLength: 256,
          },
          {
            name: "spec.series",
            path: ["spec", "series"],
            label: "令牌序列",
            kind: "password",
            required: true,
            maxLength: 256,
            hint: editing ? "令牌凭据不会回显，保存时必须重新填写。" : "持久登录令牌的序列值。",
          },
          {
            name: "spec.tokenValue",
            path: ["spec", "tokenValue"],
            label: "令牌值",
            kind: "password",
            required: true,
            maxLength: 512,
            hint: editing ? "令牌凭据不会回显，保存时必须重新填写。" : "持久登录令牌的值。",
          },
          {
            name: "spec.previousTokenValue",
            path: ["spec", "previousTokenValue"],
            label: "上一令牌值",
            kind: "password",
            maxLength: 512,
            hint: "可选，用于轮换期间的旧令牌校验。",
          },
          {
            name: "spec.lastUsed",
            path: ["spec", "lastUsed"],
            label: "最后使用时间",
            kind: "text",
            maxLength: 64,
            hint: "ISO-8601 时间，留空表示尚未使用。",
          },
        ],
      };
    case "annotationsettings":
      return {
        fields: [
          nameField(editing),
          {
            name: "spec.targetRef.group",
            path: ["spec", "targetRef", "group"],
            label: "目标组",
            kind: "text",
            section: "目标",
            required: true,
            maxLength: 253,
            hint: "例如 content.pudans.tolim.cn。",
          },
          {
            name: "spec.targetRef.kind",
            path: ["spec", "targetRef", "kind"],
            label: "目标类型",
            kind: "text",
            section: "目标",
            required: true,
            maxLength: 63,
            hint: "例如 Post。",
          },
          {
            name: "spec.formSchema",
            path: ["spec", "formSchema"],
            label: "注解表单",
            kind: "json",
            section: "表单",
            jsonShape: "array",
            required: true,
            rows: 12,
            defaultValue: "[]",
            hint: "JSON 数组形式的表单字段定义。",
          },
        ],
      };
    case "secrets":
      return {
        fields: [
          nameField(editing),
          {
            name: "type",
            path: ["type"],
            label: "类型",
            kind: "text",
            maxLength: 64,
            hint: "可选的密钥类型，例如 Opaque。",
          },
        ],
      };
  }
}

export function validateExtensionResourceValues(
  kind: ExtensionResourceKind,
  values: ParameterFormValues,
): string | null {
  if (kind === "settings") {
    const forms = parseParameterJson<unknown>(String(values["spec.forms"] ?? ""));
    if (!Array.isArray(forms) || forms.length === 0) {
      return "请至少填写一个表单定义。";
    }
    const valid = forms.every((form) => {
      const item = asRecord(form);
      return (
        typeof item.group === "string" &&
        item.group.trim().length > 0 &&
        Array.isArray(item.formSchema) &&
        item.formSchema.every(isJsonObject)
      );
    });
    if (!valid) {
      return "每个表单定义都必须包含 group 与对象数组形式的 formSchema。";
    }
  }

  if (kind === "annotationsettings") {
    const formSchema = parseParameterJson<unknown>(String(values["spec.formSchema"] ?? ""));
    if (!Array.isArray(formSchema) || formSchema.length === 0 || !formSchema.every(isJsonObject)) {
      return "注解表单必须是非空的对象数组。";
    }
  }

  return null;
}

export function buildExtensionResourceRecord(
  kind: ExtensionResourceKind,
  existing: ExtensionRecord | undefined,
  body: Record<string, unknown>,
): ExtensionRecord {
  const definition = extensionResourceDefinition(kind);
  const record = mergeExtensionRecord(existing, body, {
    apiVersion: definition.apiVersion,
    kind: definition.kindName,
  });
  const spec = record.spec ?? {};

  if (kind === "webhooks") {
    const nextSpec: Record<string, unknown> = {
      ...spec,
      events: splitList(spec.events),
    };
    if ("headers" in nextSpec) {
      const headers = toStringMap(nextSpec.headers);
      if (Object.keys(headers).length > 0) {
        nextSpec.headers = headers;
      } else {
        delete nextSpec.headers;
      }
    }
    if (nextSpec.secret === "") {
      delete nextSpec.secret;
    }
    record.spec = nextSpec;
  }

  if (kind === "configmaps") {
    record.data = toStringMap(record.data);
  }

  if (kind === "remembermetokens") {
    const nextSpec = { ...spec };
    if (!isNonBlankString(nextSpec.previousTokenValue)) {
      delete nextSpec.previousTokenValue;
    }
    if (!isNonBlankString(nextSpec.lastUsed)) {
      delete nextSpec.lastUsed;
    }
    record.spec = nextSpec;
  }

  return record;
}

export function summarizeExtensionResource(
  kind: ExtensionResourceKind,
  record: ExtensionRecord,
): ExtensionResourceSummary {
  const spec = record.spec ?? {};

  switch (kind) {
    case "webhooks": {
      const events = splitList(spec.events);
      const status = typeof spec.lastDeliveryStatus === "string" ? spec.lastDeliveryStatus : "";
      return {
        summary: typeof spec.url === "string" && spec.url ? spec.url : "未配置回调地址",
        detail: events.length > 0 ? `事件：${events.join("、")}` : "事件：全部",
        badges: [spec.enabled === false ? "已停用" : "已启用", ...(status ? [status] : [])],
      };
    }
    case "configmaps": {
      const keys = Object.keys(record.data ?? {});
      return {
        summary: `${keys.length} 个配置键`,
        detail: keys.length > 0 ? keys.slice(0, 8).join("、") : "暂无配置项",
        badges: [],
      };
    }
    case "secrets": {
      const keys = new Set([
        ...Object.keys(record.data ?? {}),
        ...Object.keys(record.stringData ?? {}),
      ]);
      return {
        summary: `${keys.size} 个键`,
        detail: keys.size > 0 ? [...keys].slice(0, 8).join("、") : "暂无密钥项",
        badges: [keys.size > 0 ? "已配置" : "未配置"],
      };
    }
    case "settings": {
      const forms = Array.isArray(spec.forms) ? spec.forms : [];
      const groups = forms
        .map((form) => asRecord(form).group)
        .filter((group): group is string => typeof group === "string" && group.length > 0);
      return {
        summary: `${forms.length} 个表单`,
        detail: groups.length > 0 ? groups.join("、") : "暂无表单分组",
        badges: [],
      };
    }
    case "remembermetokens": {
      const username = typeof spec.username === "string" ? spec.username : "";
      const lastUsed = typeof spec.lastUsed === "string" ? spec.lastUsed : "";
      return {
        summary: username || "未知用户",
        detail: lastUsed ? `最后使用：${lastUsed}` : "尚未使用",
        badges: [],
      };
    }
    case "annotationsettings": {
      const targetRef = asRecord(spec.targetRef);
      const group = typeof targetRef.group === "string" ? targetRef.group : "";
      const targetKind = typeof targetRef.kind === "string" ? targetRef.kind : "";
      const formSchema = Array.isArray(spec.formSchema) ? spec.formSchema : [];
      return {
        summary: group || targetKind ? `${group}/${targetKind}` : "未配置目标引用",
        detail: `${formSchema.length} 项表单字段`,
        badges: [],
      };
    }
  }
}

function nameField(editing: boolean): ParameterField {
  return {
    name: "metadata.name",
    path: ["metadata", "name"],
    label: "名称",
    kind: "text",
    required: true,
    maxLength: 63,
    readOnly: editing,
    hint: editing ? "资源名称创建后不可修改。" : "小写字母、数字、- 和 .，创建后不可修改。",
  };
}

function toRecord(value: unknown): ExtensionRecord {
  return normalizeExtensionRecord(value);
}

function isNonBlankString(value: unknown): boolean {
  return typeof value === "string" && value.trim().length > 0;
}

function isJsonObject(value: unknown): boolean {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
