import { formatBeijingClock, toDate } from "@/lib/date";
import type { MultiSelectOption } from "@/modules/workspace/ai/library/components/multi-select";
import { formatDuration, formatRelativeTime } from "@/modules/workspace/ai/routing/lib/model";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type {
  AiRuntimeAgentItem,
  AiRuntimeAgentSpec,
  AiRuntimeProvider,
  AiRuntimeView,
  RelatedResourceFieldKey,
  RelatedResourceItem,
  SandboxState,
} from "./api";

export type BadgeVariant = "default" | "secondary" | "destructive" | "outline" | "ghost" | "link";

export type RuntimeAction = "start" | "stop" | "restart" | "destroy";

export type RuntimeConfirmAction = Exclude<RuntimeAction, "start">;

export type AgentRuntimeRow = {
  name: string;
  agent?: AiRuntimeAgentItem;
  runtime?: AiRuntimeView;
};

export type RelatedResourceOption = MultiSelectOption;

export const RUNTIME_OPERATIONS = {
  create: "create",
  update: "update",
  delete: "delete",
  // 启动、停止、重启按钮共用 toggle 操作键。
  toggle: "toggle",
  execute: "execute",
  // 文件按钮读取与写入共用 update 操作键。
  files: "update",
  logs: "export",
} as const;

export const RUNTIME_ACTION_SUCCESS: Record<RuntimeAction, string> = {
  start: "运行时已启动。",
  stop: "运行时已停止。",
  restart: "运行时已重建。",
  destroy: "运行时已销毁。",
};

export const RUNTIME_PROVIDER_OPTIONS: { value: AiRuntimeProvider; label: string }[] = [
  { value: "NONE", label: "未配置" },
  { value: "ALIYUN", label: "阿里云" },
  { value: "TENCENT", label: "腾讯云" },
];

export const SANDBOX_PROVIDER_OPTIONS: { value: "ALIYUN" | "TENCENT"; label: string }[] = [
  { value: "ALIYUN", label: "阿里云" },
  { value: "TENCENT", label: "腾讯云" },
];

export const VISIBILITY_OPTIONS: { value: "PRIVATE" | "TEAM" | "RESTRICTED"; label: string }[] = [
  { value: "PRIVATE", label: "私有" },
  { value: "TEAM", label: "团队" },
  { value: "RESTRICTED", label: "受限" },
];

export const VISIBILITY_HINTS: Record<"PRIVATE" | "TEAM" | "RESTRICTED", string> = {
  PRIVATE: "仅所有者本人可访问。",
  TEAM: "所属团队成员可访问。",
  RESTRICTED: "按受限范围访问，具体以团队策略为准。",
};

export const AGENT_STATUS_OPTIONS: { value: "ACTIVE" | "INACTIVE"; label: string }[] = [
  { value: "ACTIVE", label: "已启用" },
  { value: "INACTIVE", label: "已停用" },
];

export const RUNTIME_STATE_FILTER_OPTIONS: { value: SandboxState | "NONE"; label: string }[] = [
  { value: "NONE", label: "未创建" },
  { value: "CREATING", label: "创建中" },
  { value: "RUNNING", label: "运行中" },
  { value: "STOPPING", label: "停止中" },
  { value: "STOPPED", label: "已停止" },
  { value: "FAILED", label: "失败" },
  { value: "DESTROYED", label: "已销毁" },
];

export const DEFAULT_ROUTE_GROUP_VALUE = "__none__";

export const DEFAULT_CHAT_MODE_VALUE = "__none__";

export const DEFAULT_AGENT_RUNTIME = {
  provider: "NONE" as AiRuntimeProvider,
  image: "",
  entrypoint: "",
  cpu: 1,
  memoryMb: 1024,
  timeoutSeconds: 3600,
  autoStart: false,
  networkEnabled: true,
  workdir: "/workspace",
} as const;

export const FILE_ENCODING_TEXT = "utf-8";

export const FILE_ENCODING_BASE64 = "base64";

export const LOG_LINE_LIMIT = 2000;

export const RELATED_RESOURCE_FIELDS: {
  key: RelatedResourceFieldKey;
  label: string;
  placeholder: string;
  emptyMessage: string;
}[] = [
  { key: "skills", label: "技能", placeholder: "选择技能", emptyMessage: "还没有可用技能" },
  {
    key: "mcpServers",
    label: "MCP 服务器",
    placeholder: "选择 MCP 服务器",
    emptyMessage: "还没有可用 MCP 服务器",
  },
  { key: "hooks", label: "Hook", placeholder: "选择 Hook", emptyMessage: "还没有可用 Hook" },
  {
    key: "environmentVariables",
    label: "环境变量",
    placeholder: "选择环境变量",
    emptyMessage: "还没有可用环境变量",
  },
  {
    key: "subagents",
    label: "子代理",
    placeholder: "选择子代理",
    emptyMessage: "还没有可用子代理",
  },
  {
    key: "slashCommands",
    label: "斜杠命令",
    placeholder: "选择斜杠命令",
    emptyMessage: "还没有可用斜杠命令",
  },
  {
    key: "instructions",
    label: "指令",
    placeholder: "选择指令",
    emptyMessage: "还没有可用指令",
  },
];

const RUNTIME_STATE_LABELS: Record<SandboxState, string> = {
  CREATING: "创建中",
  RUNNING: "运行中",
  STOPPING: "停止中",
  STOPPED: "已停止",
  FAILED: "失败",
  DESTROYED: "已销毁",
};

const VISIBILITY_LABELS: Record<"PRIVATE" | "TEAM" | "RESTRICTED", string> = {
  PRIVATE: "私有",
  TEAM: "团队",
  RESTRICTED: "受限",
};

const AGENT_STATUS_LABELS: Record<"ACTIVE" | "INACTIVE", string> = {
  ACTIVE: "已启用",
  INACTIVE: "已停用",
};

const RUNTIME_STATE_BADGE_VARIANTS: Record<SandboxState, BadgeVariant> = {
  CREATING: "outline",
  RUNNING: "secondary",
  STOPPING: "outline",
  STOPPED: "outline",
  FAILED: "destructive",
  DESTROYED: "outline",
};

export function mergeAgentRuntimeRows(
  agents: readonly AiRuntimeAgentItem[],
  runtimes: readonly AiRuntimeView[],
): AgentRuntimeRow[] {
  const runtimeByAgent = new Map(runtimes.map((runtime) => [runtime.agent, runtime]));
  const rows: AgentRuntimeRow[] = agents.map((agent) => ({
    name: agent.metadata.name,
    agent,
    runtime: runtimeByAgent.get(agent.metadata.name),
  }));
  const knownAgents = new Set(agents.map((agent) => agent.metadata.name));
  for (const runtime of runtimes) {
    if (!knownAgents.has(runtime.agent)) {
      rows.push({ name: runtime.agent, runtime });
    }
  }
  return rows;
}

export function runtimeProviderLabel(provider: AiRuntimeProvider | string | undefined): string {
  return (
    RUNTIME_PROVIDER_OPTIONS.find((option) => option.value === provider)?.label ??
    provider ??
    "未配置"
  );
}

export function runtimeStateLabel(state: SandboxState | null | undefined): string {
  return state ? RUNTIME_STATE_LABELS[state] : "未创建";
}

export function runtimeStateBadgeVariant(state: SandboxState | null | undefined): BadgeVariant {
  return state ? RUNTIME_STATE_BADGE_VARIANTS[state] : "outline";
}

export function visibilityLabel(value: "PRIVATE" | "TEAM" | "RESTRICTED" | undefined): string {
  return value ? VISIBILITY_LABELS[value] : VISIBILITY_LABELS.TEAM;
}

export function agentStatusLabel(value: "ACTIVE" | "INACTIVE" | undefined): string {
  return value ? AGENT_STATUS_LABELS[value] : AGENT_STATUS_LABELS.ACTIVE;
}

export function rowRuntimeState(row: AgentRuntimeRow): SandboxState | "NONE" {
  return row.runtime?.state ?? "NONE";
}

export function rowRuntimeProvider(row: AgentRuntimeRow): AiRuntimeProvider {
  return row.runtime?.provider ?? row.agent?.spec.runtime?.provider ?? "NONE";
}

export function agentDisplayName(agent: AiRuntimeAgentItem | undefined, fallback: string): string {
  return agent?.spec.displayName || fallback;
}

export function relatedResourceOptions(
  items: readonly RelatedResourceItem[],
): RelatedResourceOption[] {
  return items.map((item) => ({
    value: item.metadata.name,
    label: item.spec?.displayName || item.spec?.name || item.metadata.name,
    hint:
      item.spec?.enabled === false
        ? "已停用"
        : item.spec?.name && item.spec?.displayName
          ? item.spec.name
          : item.spec?.description,
  }));
}

export function relatedResourceCount(
  spec: AiRuntimeAgentSpec | undefined,
  field: RelatedResourceFieldKey,
): number {
  return spec?.[field]?.length ?? 0;
}

export function fileEncodingLabel(encoding: string | undefined): string {
  return encoding?.toLowerCase() === FILE_ENCODING_BASE64 ? "Base64" : "文本";
}

export function formatLogTime(value: string | null | undefined): string {
  return toDate(value) ? formatBeijingClock(value) : "--:--:--";
}

export function formatRuntimeLastActive(value: string | null | undefined): string {
  return formatRelativeTime(value ?? undefined);
}

export function formatRuntimeDateTime(value: string | null | undefined): string {
  return formatUserDateTime(value ?? undefined);
}

export function formatExecDuration(value: number | undefined): string {
  return formatDuration(value);
}

export function logLineKey(event: {
  timestamp?: string | null;
  stream?: string | null;
  line: string;
}): string {
  return `${event.timestamp ?? ""}|${event.stream ?? ""}|${event.line}`;
}
