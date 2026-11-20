import { type Control, Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bot, CircleAlert } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { MultiSelect } from "@/modules/workspace/ai/library/components/multi-select";
import { AI_AGENTS_QUERY_KEY } from "@/modules/workspace/ai/library/lib/agents";
import { resourceGenerateName } from "@/modules/workspace/ai/library/lib/model";
import { fetchAllRouteGroups } from "@/modules/workspace/ai/providers/lib/api";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";
import { fetchChatModes } from "@/modules/workspace/index/lib/chat-api";

import {
  AI_AGENT_KIND,
  AI_AGENT_RUNTIME_RELATED_QUERY_KEY,
  AI_API_VERSION,
  type AiRuntimeAgentItem,
  type AiRuntimeAgentSpec,
  type AiRuntimeProvider,
  type RelatedResourceFieldKey,
  type RelatedResourceItem,
  createRuntimeAgent,
  fetchRelatedResources,
  resolveAgentRuntimeError,
  updateRuntimeAgent,
} from "../lib/api";
import {
  AGENT_STATUS_OPTIONS,
  DEFAULT_AGENT_RUNTIME,
  DEFAULT_CHAT_MODE_VALUE,
  DEFAULT_ROUTE_GROUP_VALUE,
  RELATED_RESOURCE_FIELDS,
  RUNTIME_PROVIDER_OPTIONS,
  VISIBILITY_HINTS,
  VISIBILITY_OPTIONS,
  relatedResourceOptions,
  runtimeProviderLabel,
} from "../lib/model";

const visibilityValues = ["PRIVATE", "TEAM", "RESTRICTED"] as const;

const statusValues = ["ACTIVE", "INACTIVE"] as const;

const runtimeProviderValues = ["NONE", "ALIYUN", "TENCENT"] as const;

const agentSchema = z.object({
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
  prompt: z.string().trim().max(20000, "系统提示词不能超过 20000 个字符。"),
  visibility: z.enum(visibilityValues),
  status: z.enum(statusValues),
  defaultRouteGroup: z.string(),
  defaultChatMode: z.string(),
  runtimeProvider: z.enum(runtimeProviderValues),
  image: z.string().trim().max(512, "镜像名称过长。"),
  entrypoint: z.string().trim().max(4000, "入口命令过长。"),
  cpu: z.coerce.number().min(0.1, "CPU 至少为 0.1。").max(16, "CPU 不能超过 16。"),
  memoryMb: z.coerce
    .number()
    .int("内存必须是整数。")
    .min(128, "内存至少为 128 MB。")
    .max(65536, "内存不能超过 65536 MB。"),
  timeoutSeconds: z.coerce
    .number()
    .int("超时必须是整数。")
    .min(60, "超时至少为 60 秒。")
    .max(86400, "超时不能超过 86400 秒。"),
  autoStart: z.boolean(),
  networkEnabled: z.boolean(),
  workdir: z.string().trim().min(1, "请输入工作目录。").max(500, "工作目录过长。"),
  skills: z.array(z.string()),
  mcpServers: z.array(z.string()),
  hooks: z.array(z.string()),
  environmentVariables: z.array(z.string()),
  subagents: z.array(z.string()),
  slashCommands: z.array(z.string()),
  instructions: z.array(z.string()),
});

type AgentFormValues = z.infer<typeof agentSchema>;

type AgentDialogProps = {
  agent?: AiRuntimeAgentItem;
  configuredProviders: ReadonlySet<string>;
  onClose: () => void;
};

export function AgentDialog({ agent, configuredProviders, onClose }: AgentDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<AgentFormValues>({
    resolver: zodResolver(agentSchema) as Resolver<AgentFormValues>,
    defaultValues: toFormValues(agent),
  });
  const runtimeProvider = useWatch({ control: form.control, name: "runtimeProvider" });
  const visibility = useWatch({ control: form.control, name: "visibility" });

  const relatedQuery = useQuery({
    queryKey: AI_AGENT_RUNTIME_RELATED_QUERY_KEY,
    queryFn: fetchRelatedResources,
    retry: false,
  });
  const routeGroupsQuery = useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
    retry: false,
  });
  const chatModesQuery = useQuery({
    queryKey: ["chat-modes"],
    queryFn: fetchChatModes,
    retry: false,
  });
  const currentUserQuery = useQuery({
    queryKey: ["account-profile"],
    queryFn: async () => (await apiClient.user.getCurrentUser()).data,
    retry: false,
  });

  const saveMutation = useMutation({
    mutationFn: ({ values, ownerUserId }: { values: AgentFormValues; ownerUserId: string }) => {
      const spec = toSpec(values, agent?.spec, ownerUserId);
      if (agent) {
        return updateRuntimeAgent({ ...agent, spec });
      }
      return createRuntimeAgent({
        apiVersion: AI_API_VERSION,
        kind: AI_AGENT_KIND,
        metadata: { generateName: resourceGenerateName(values.displayName, "agent") },
        spec,
      });
    },
    onSuccess: async (saved) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["ai-agent-runtime-agents"] }),
        queryClient.invalidateQueries({ queryKey: AI_AGENTS_QUERY_KEY }),
      ]);
      toast.success(agent ? "Agent 已保存。" : "Agent 已创建。", {
        description: saved.spec.displayName || saved.metadata.name,
      });
      onClose();
    },
    onError: (error) => toast.error(resolveAgentRuntimeError(error)),
  });

  const runtimeProviderMissing =
    runtimeProvider !== "NONE" && !configuredProviders.has(runtimeProvider);

  const onSubmit = form.handleSubmit(
    (values) => {
      const ownerUserId = agent?.spec.ownerUserId ?? currentUserQuery.data?.spec.uid;
      if (!ownerUserId) {
        toast.error("无法获取当前登录用户信息，请刷新页面后重试。");
        return;
      }
      saveMutation.mutate({ values, ownerUserId });
    },
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Bot className="size-5" />}
      title={agent ? `编辑 ${agent.spec.displayName || agent.metadata.name}` : "新建 Agent"}
      description="配置 Agent 的基本信息、沙箱运行时与关联资源。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-3xl"
      maxHeightRatio={0.7}
      submitLabel={agent ? "保存更改" : "创建 Agent"}
    >
      <FormDialogSection title="基本信息">
        <p className="text-xs text-muted-foreground">
          团队：{agent?.spec.team ?? "default"} · 所有者：
          {agent?.spec.ownerUserId ?? currentUserQuery.data?.spec.uid ?? "当前登录用户"}
          {agent ? ` · 创建于 ${formatUserDateTime(agent.metadata.creationTimestamp)}` : ""}
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="agent-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="agent-display-name"
              placeholder="例如：代码助手"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="状态"
            htmlFor="agent-status"
            hint="停用后运行时接口会拒绝访问该 Agent。"
            error={form.formState.errors.status?.message}
          >
            <Controller
              control={form.control}
              name="status"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as "ACTIVE" | "INACTIVE")}
                >
                  <SelectTrigger id="agent-status" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {AGENT_STATUS_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="可见性"
            htmlFor="agent-visibility"
            hint={VISIBILITY_HINTS[visibility]}
            error={form.formState.errors.visibility?.message}
          >
            <Controller
              control={form.control}
              name="visibility"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) =>
                    field.onChange(value as "PRIVATE" | "TEAM" | "RESTRICTED")
                  }
                >
                  <SelectTrigger id="agent-visibility" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {VISIBILITY_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
          <FormField
            label="默认路由组"
            htmlFor="agent-route-group"
            hint="留空时使用 AI 设置中的默认路由组。"
            error={form.formState.errors.defaultRouteGroup?.message}
          >
            <Controller
              control={form.control}
              name="defaultRouteGroup"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="agent-route-group" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={DEFAULT_ROUTE_GROUP_VALUE}>不指定</SelectItem>
                    {(routeGroupsQuery.data ?? []).map((group) => (
                      <SelectItem key={group.metadata.name} value={group.metadata.name}>
                        {group.spec.displayName || group.metadata.name}
                      </SelectItem>
                    ))}
                    <UnlistedOption
                      value={field.value}
                      knownValues={(routeGroupsQuery.data ?? []).map(
                        (group) => group.metadata.name,
                      )}
                    />
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        </div>
        <FormField
          label="默认对话模式"
          htmlFor="agent-chat-mode"
          hint="留空时使用聊天页当前模式。"
          error={form.formState.errors.defaultChatMode?.message}
        >
          <Controller
            control={form.control}
            name="defaultChatMode"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="agent-chat-mode" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={DEFAULT_CHAT_MODE_VALUE}>不指定</SelectItem>
                  {(chatModesQuery.data ?? []).map((mode) => (
                    <SelectItem key={mode.name} value={mode.name}>
                      {mode.displayName || mode.name}
                    </SelectItem>
                  ))}
                  <UnlistedOption
                    value={field.value}
                    knownValues={(chatModesQuery.data ?? []).map((mode) => mode.name)}
                  />
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
        {routeGroupsQuery.isError || chatModesQuery.isError ? (
          <p className="text-xs text-destructive">
            部分下拉选项加载失败，可稍后重试或保留为「不指定」。
          </p>
        ) : null}
        <FormField
          label="描述"
          htmlFor="agent-description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            id="agent-description"
            rows={2}
            placeholder="说明该 Agent 的用途与适用场景。"
            {...form.register("description")}
          />
        </FormField>
        <FormField
          label="系统提示词"
          htmlFor="agent-prompt"
          hint="作为 Agent 的系统指令注入对话。"
          error={form.formState.errors.prompt?.message}
        >
          <Textarea
            id="agent-prompt"
            rows={4}
            placeholder="你是一个严谨的软件工程助手……"
            className="font-mono text-xs"
            {...form.register("prompt")}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="运行时配置">
        {runtimeProviderMissing ? (
          <Alert variant="destructive">
            <CircleAlert />
            <AlertTitle>提供方尚未配置</AlertTitle>
            <AlertDescription>
              尚未启用 {runtimeProviderLabel(runtimeProvider)} 沙箱提供方，启动该 Agent
              会失败。请先在 「沙箱提供方」中完成配置。
            </AlertDescription>
          </Alert>
        ) : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="沙箱提供方"
            htmlFor="agent-runtime-provider"
            hint="选择「未配置」时不能启动沙箱。"
            error={form.formState.errors.runtimeProvider?.message}
          >
            <Controller
              control={form.control}
              name="runtimeProvider"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as AiRuntimeProvider)}
                >
                  <SelectTrigger id="agent-runtime-provider" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {RUNTIME_PROVIDER_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
          <FormField
            label="工作目录"
            htmlFor="agent-runtime-workdir"
            hint="沙箱内命令与文件的默认目录。"
            error={form.formState.errors.workdir?.message}
          >
            <Input
              id="agent-runtime-workdir"
              className="font-mono"
              disabled={runtimeProvider === "NONE"}
              {...form.register("workdir")}
            />
          </FormField>
        </div>
        <FormField
          label="镜像"
          htmlFor="agent-runtime-image"
          hint="留空时使用沙箱提供方配置的默认镜像。"
          error={form.formState.errors.image?.message}
        >
          <Input
            id="agent-runtime-image"
            placeholder="留空使用提供方默认镜像"
            className="font-mono"
            disabled={runtimeProvider === "NONE"}
            {...form.register("image")}
          />
        </FormField>
        <FormField
          label="入口命令"
          htmlFor="agent-runtime-entrypoint"
          hint="沙箱创建后执行的启动命令，可留空。"
          error={form.formState.errors.entrypoint?.message}
        >
          <Textarea
            id="agent-runtime-entrypoint"
            rows={2}
            placeholder="例如：bash -lc 'sleep infinity'"
            className="font-mono text-xs"
            disabled={runtimeProvider === "NONE"}
            {...form.register("entrypoint")}
          />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField
            label="CPU（核）"
            htmlFor="agent-runtime-cpu"
            hint="0.1 – 16"
            error={form.formState.errors.cpu?.message}
          >
            <Input
              id="agent-runtime-cpu"
              type="number"
              step="0.1"
              inputMode="decimal"
              disabled={runtimeProvider === "NONE"}
              {...form.register("cpu")}
            />
          </FormField>
          <FormField
            label="内存（MB）"
            htmlFor="agent-runtime-memory"
            hint="128 – 65536"
            error={form.formState.errors.memoryMb?.message}
          >
            <Input
              id="agent-runtime-memory"
              type="number"
              inputMode="numeric"
              disabled={runtimeProvider === "NONE"}
              {...form.register("memoryMb")}
            />
          </FormField>
          <FormField
            label="超时（秒）"
            htmlFor="agent-runtime-timeout"
            hint="60 – 86400"
            error={form.formState.errors.timeoutSeconds?.message}
          >
            <Input
              id="agent-runtime-timeout"
              type="number"
              inputMode="numeric"
              disabled={runtimeProvider === "NONE"}
              {...form.register("timeoutSeconds")}
            />
          </FormField>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Controller
            control={form.control}
            name="autoStart"
            render={({ field }) => (
              <div className="flex items-center justify-between gap-4 rounded-lg border p-4">
                <div className="space-y-1">
                  <p className="text-sm font-medium">自动启动</p>
                  <p className="text-xs text-muted-foreground">
                    记录自动启动偏好，实际启动仍由运行时操作触发。
                  </p>
                </div>
                <Switch
                  checked={field.value}
                  onCheckedChange={field.onChange}
                  disabled={runtimeProvider === "NONE" || saveMutation.isPending}
                  aria-label="自动启动"
                />
              </div>
            )}
          />
          <Controller
            control={form.control}
            name="networkEnabled"
            render={({ field }) => (
              <div className="flex items-center justify-between gap-4 rounded-lg border p-4">
                <div className="space-y-1">
                  <p className="text-sm font-medium">网络访问</p>
                  <p className="text-xs text-muted-foreground">允许沙箱访问外部网络。</p>
                </div>
                <Switch
                  checked={field.value}
                  onCheckedChange={field.onChange}
                  disabled={runtimeProvider === "NONE" || saveMutation.isPending}
                  aria-label="网络访问"
                />
              </div>
            )}
          />
        </div>
        {runtimeProvider === "NONE" ? (
          <p className="text-xs text-muted-foreground">
            选择沙箱提供方后，镜像、资源与超时配置才会在启动时生效。
          </p>
        ) : null}
      </FormDialogSection>

      <FormDialogSection title="关联资源">
        <p className="text-xs text-muted-foreground">
          关联的资源会在该 Agent 的对话与沙箱中注入；留空表示不注入。
        </p>
        {relatedQuery.isError ? (
          <Alert variant="destructive">
            <CircleAlert />
            <AlertTitle>关联资源加载失败</AlertTitle>
            <AlertDescription className="space-y-2">
              <p>{resolveAgentRuntimeError(relatedQuery.error)}</p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => void relatedQuery.refetch()}
              >
                重试
              </Button>
            </AlertDescription>
          </Alert>
        ) : null}
        <div className="grid gap-4 sm:grid-cols-2">
          {RELATED_RESOURCE_FIELDS.map((resourceField) => (
            <RelatedResourceField
              key={resourceField.key}
              control={form.control}
              fieldKey={resourceField.key}
              label={resourceField.label}
              placeholder={resourceField.placeholder}
              emptyMessage={resourceField.emptyMessage}
              items={relatedQuery.data?.[resourceField.key] ?? []}
              loading={relatedQuery.isPending}
              disabled={saveMutation.isPending}
            />
          ))}
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}

type RelatedResourceFieldProps = {
  control: Control<AgentFormValues>;
  fieldKey: RelatedResourceFieldKey;
  label: string;
  placeholder: string;
  emptyMessage: string;
  items: RelatedResourceItem[];
  loading: boolean;
  disabled: boolean;
};

function RelatedResourceField({
  control,
  fieldKey,
  label,
  placeholder,
  emptyMessage,
  items,
  loading,
  disabled,
}: RelatedResourceFieldProps) {
  return (
    <FormField label={label} htmlFor={`agent-related-${fieldKey}`}>
      <Controller
        control={control}
        name={fieldKey}
        render={({ field }) => (
          <MultiSelect
            id={`agent-related-${fieldKey}`}
            value={field.value ?? []}
            onChange={field.onChange}
            options={relatedResourceOptions(items)}
            placeholder={placeholder}
            emptyMessage={emptyMessage}
            loading={loading}
            disabled={disabled}
          />
        )}
      />
    </FormField>
  );
}

type UnlistedOptionProps = {
  value: string;
  knownValues: readonly string[];
};

function UnlistedOption({ value, knownValues }: UnlistedOptionProps) {
  if (!value || value === DEFAULT_ROUTE_GROUP_VALUE || value === DEFAULT_CHAT_MODE_VALUE) {
    return null;
  }
  if (knownValues.includes(value)) {
    return null;
  }
  return <SelectItem value={value}>{value}</SelectItem>;
}

function toFormValues(agent: AiRuntimeAgentItem | undefined): AgentFormValues {
  const runtime = agent?.spec.runtime;
  return {
    displayName: agent?.spec.displayName ?? "",
    description: agent?.spec.description ?? "",
    prompt: agent?.spec.prompt ?? "",
    visibility: agent?.spec.visibility ?? "TEAM",
    status: agent?.spec.status ?? "ACTIVE",
    defaultRouteGroup: agent?.spec.defaultRouteGroup || DEFAULT_ROUTE_GROUP_VALUE,
    defaultChatMode: agent?.spec.defaultChatMode || DEFAULT_CHAT_MODE_VALUE,
    runtimeProvider: runtime?.provider ?? DEFAULT_AGENT_RUNTIME.provider,
    image: runtime?.image ?? "",
    entrypoint: runtime?.entrypoint ?? "",
    cpu: runtime?.cpu ?? DEFAULT_AGENT_RUNTIME.cpu,
    memoryMb: runtime?.memoryMb ?? DEFAULT_AGENT_RUNTIME.memoryMb,
    timeoutSeconds: runtime?.timeoutSeconds ?? DEFAULT_AGENT_RUNTIME.timeoutSeconds,
    autoStart: runtime?.autoStart ?? DEFAULT_AGENT_RUNTIME.autoStart,
    networkEnabled: runtime?.networkEnabled ?? DEFAULT_AGENT_RUNTIME.networkEnabled,
    workdir: runtime?.workdir ?? DEFAULT_AGENT_RUNTIME.workdir,
    skills: agent?.spec.skills ?? [],
    mcpServers: agent?.spec.mcpServers ?? [],
    hooks: agent?.spec.hooks ?? [],
    environmentVariables: agent?.spec.environmentVariables ?? [],
    subagents: agent?.spec.subagents ?? [],
    slashCommands: agent?.spec.slashCommands ?? [],
    instructions: agent?.spec.instructions ?? [],
  };
}

function toSpec(
  values: AgentFormValues,
  base: AiRuntimeAgentSpec | undefined,
  ownerUserId: string,
): AiRuntimeAgentSpec {
  return {
    team: base?.team ?? "default",
    ownerUserId,
    displayName: values.displayName.trim(),
    description: values.description.trim(),
    prompt: values.prompt.trim(),
    visibility: values.visibility,
    status: values.status,
    defaultRouteGroup:
      values.defaultRouteGroup === DEFAULT_ROUTE_GROUP_VALUE ? undefined : values.defaultRouteGroup,
    defaultChatMode:
      values.defaultChatMode === DEFAULT_CHAT_MODE_VALUE ? undefined : values.defaultChatMode,
    skills: values.skills,
    mcpServers: values.mcpServers,
    hooks: values.hooks,
    environmentVariables: values.environmentVariables,
    subagents: values.subagents,
    slashCommands: values.slashCommands,
    instructions: values.instructions,
    runtime: {
      provider: values.runtimeProvider,
      image: values.image.trim(),
      entrypoint: values.entrypoint.trim(),
      cpu: values.cpu,
      memoryMb: values.memoryMb,
      timeoutSeconds: values.timeoutSeconds,
      autoStart: values.autoStart,
      networkEnabled: values.networkEnabled,
      workdir: values.workdir.trim(),
    },
    metadata: base?.metadata ?? {},
  };
}
