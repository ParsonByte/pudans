import { useMemo } from "react";
import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bot } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  fetchAllProviderModels,
  fetchAllRouteGroups,
} from "@/modules/workspace/ai/providers/lib/api";

import { AgentsField, EnabledField } from "../../components/library-dialog-fields";
import { MultiSelect, type MultiSelectOption } from "../../components/multi-select";
import { resolveLibraryApiError } from "../../lib/api";
import { resourceGenerateName, sortBy } from "../../lib/model";
import { MCP_SERVERS_QUERY_KEY, fetchMcpServers } from "../../mcp-servers/lib/api";
import { mcpDisplayName } from "../../mcp-servers/lib/model";
import { SKILLS_QUERY_KEY, fetchSkills } from "../../skills/lib/api";
import { skillDisplayName } from "../../skills/lib/model";
import {
  AI_API_VERSION,
  AI_SUBAGENT_KIND,
  SUBAGENTS_QUERY_KEY,
  type SubagentItem,
  type SubagentSpec,
  createSubagent,
  updateSubagent,
} from "../lib/api";

const UNSET_OPTION = "__unset__";

const subagentSchema = z.object({
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
  prompt: z.string().trim().min(1, "请输入子代理提示词。").max(20000, "提示词过长。"),
  routeGroup: z.string().trim().max(200, "路由组名称过长。"),
  model: z.string().trim().max(200, "模型名称过长。"),
  skills: z.array(z.string()),
  mcpServers: z.array(z.string()),
  agents: z.array(z.string()),
  enabled: z.boolean(),
});

type SubagentFormValues = z.infer<typeof subagentSchema>;

type SubagentDialogProps = {
  subagent?: SubagentItem;
  onClose: () => void;
};

function toFormValues(subagent: SubagentItem | undefined): SubagentFormValues {
  return {
    displayName: subagent?.spec.displayName ?? "",
    description: subagent?.spec.description ?? "",
    prompt: subagent?.spec.prompt ?? "",
    routeGroup: subagent?.spec.routeGroup ?? "",
    model: subagent?.spec.model ?? "",
    skills: subagent?.spec.skills ?? [],
    mcpServers: subagent?.spec.mcpServers ?? [],
    agents: subagent?.spec.agents ?? [],
    enabled: subagent?.spec.enabled !== false,
  };
}

function toSpec(values: SubagentFormValues): SubagentSpec {
  return {
    displayName: values.displayName.trim(),
    description: values.description.trim() || undefined,
    prompt: values.prompt.trim(),
    routeGroup: values.routeGroup.trim() || undefined,
    model: values.model.trim() || undefined,
    skills: values.skills,
    mcpServers: values.mcpServers,
    enabled: values.enabled,
    agents: values.agents,
  };
}

export function SubagentDialog({ subagent, onClose }: SubagentDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<SubagentFormValues>({
    resolver: zodResolver(subagentSchema) as Resolver<SubagentFormValues>,
    defaultValues: toFormValues(subagent),
  });
  const skillsQuery = useQuery({
    queryKey: SKILLS_QUERY_KEY,
    queryFn: fetchSkills,
    retry: false,
  });
  const mcpServersQuery = useQuery({
    queryKey: MCP_SERVERS_QUERY_KEY,
    queryFn: fetchMcpServers,
    retry: false,
  });
  const routeGroupsQuery = useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
    retry: false,
  });
  const modelsQuery = useQuery({
    queryKey: ["ai-provider-models", "all"],
    queryFn: fetchAllProviderModels,
    retry: false,
  });

  const skillOptions = useMemo<MultiSelectOption[]>(
    () =>
      sortBy(skillsQuery.data ?? [], skillDisplayName).map((skill) => ({
        value: skill.metadata.name,
        label: skillDisplayName(skill),
      })),
    [skillsQuery.data],
  );
  const mcpOptions = useMemo<MultiSelectOption[]>(
    () =>
      sortBy(mcpServersQuery.data ?? [], mcpDisplayName).map((server) => ({
        value: server.metadata.name,
        label: mcpDisplayName(server),
      })),
    [mcpServersQuery.data],
  );
  const routeGroups = useMemo(() => routeGroupsQuery.data ?? [], [routeGroupsQuery.data]);
  const models = useMemo(
    () =>
      sortBy(
        (modelsQuery.data ?? []).filter(
          (model) => model.spec.modelType === "CHAT" && model.spec.enabled !== false,
        ),
        (model) => model.spec.displayName || model.spec.modelCode,
      ),
    [modelsQuery.data],
  );

  const saveMutation = useMutation({
    mutationFn: (values: SubagentFormValues) => {
      const spec = toSpec(values);
      if (subagent) {
        return updateSubagent({ ...subagent, spec });
      }
      return createSubagent({
        apiVersion: AI_API_VERSION,
        kind: AI_SUBAGENT_KIND,
        metadata: { generateName: resourceGenerateName(values.displayName, "subagent") },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: SUBAGENTS_QUERY_KEY });
      toast.success(subagent ? "子代理已保存。" : "子代理已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "子代理")),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Bot className="size-5" />}
      title={
        subagent ? `编辑 ${subagent.spec.displayName || subagent.metadata.name}` : "新建子代理"
      }
      description="配置可被主 Agent 调用的专用子代理及其可用资源。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-3xl"
      submitLabel={subagent ? "保存更改" : "创建子代理"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="subagent-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="subagent-display-name"
              placeholder="例如：代码审查"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="描述"
            htmlFor="subagent-description"
            error={form.formState.errors.description?.message}
          >
            <Input
              id="subagent-description"
              placeholder="说明子代理的职责"
              {...form.register("description")}
            />
          </FormField>
        </div>
        <FormField
          label="提示词"
          htmlFor="subagent-prompt"
          hint="定义子代理的角色、可用工具与输出要求。"
          error={form.formState.errors.prompt?.message}
        >
          <Textarea
            id="subagent-prompt"
            rows={8}
            placeholder="你是一个专注于代码审查的子代理…"
            className="font-mono text-xs"
            {...form.register("prompt")}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="模型选择">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="路由组"
            htmlFor="subagent-route-group"
            hint={routeGroupsQuery.isError ? "无法加载路由组。" : "留空时使用 Agent 的默认路由组。"}
            error={form.formState.errors.routeGroup?.message}
          >
            <Controller
              control={form.control}
              name="routeGroup"
              render={({ field }) => (
                <Select
                  value={field.value || UNSET_OPTION}
                  onValueChange={(value) => field.onChange(value === UNSET_OPTION ? "" : value)}
                >
                  <SelectTrigger id="subagent-route-group" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={UNSET_OPTION}>使用 Agent 默认</SelectItem>
                    {field.value &&
                    !routeGroups.some((group) => group.metadata.name === field.value) ? (
                      <SelectItem value={field.value}>{field.value}</SelectItem>
                    ) : null}
                    {routeGroups.map((group) => (
                      <SelectItem key={group.metadata.name} value={group.metadata.name}>
                        {group.spec.displayName || group.metadata.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
          <FormField
            label="模型"
            htmlFor="subagent-model"
            hint={modelsQuery.isError ? "无法加载模型。" : "留空时由路由组选择模型。"}
            error={form.formState.errors.model?.message}
          >
            <Controller
              control={form.control}
              name="model"
              render={({ field }) => (
                <Select
                  value={field.value || UNSET_OPTION}
                  onValueChange={(value) => field.onChange(value === UNSET_OPTION ? "" : value)}
                >
                  <SelectTrigger id="subagent-model" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={UNSET_OPTION}>由路由组选择</SelectItem>
                    {field.value && !models.some((model) => model.metadata.name === field.value) ? (
                      <SelectItem value={field.value}>{field.value}</SelectItem>
                    ) : null}
                    {models.map((model) => (
                      <SelectItem key={model.metadata.name} value={model.metadata.name}>
                        {model.spec.displayName || model.spec.modelCode}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="可用资源">
        <FormField label="技能" htmlFor="subagent-skills" hint="子代理可读取的技能列表。">
          <Controller
            control={form.control}
            name="skills"
            render={({ field }) => (
              <MultiSelect
                id="subagent-skills"
                value={field.value}
                onChange={field.onChange}
                options={skillOptions}
                placeholder="选择技能"
                searchPlaceholder="搜索技能"
                emptyMessage={skillsQuery.isError ? "无法加载技能列表。" : "暂无技能。"}
                loading={skillsQuery.isPending}
              />
            )}
          />
        </FormField>
        <FormField
          label="MCP 服务器"
          htmlFor="subagent-mcp-servers"
          hint="子代理可调用的 MCP 工具来源。"
        >
          <Controller
            control={form.control}
            name="mcpServers"
            render={({ field }) => (
              <MultiSelect
                id="subagent-mcp-servers"
                value={field.value}
                onChange={field.onChange}
                options={mcpOptions}
                placeholder="选择 MCP 服务器"
                searchPlaceholder="搜索 MCP 服务器"
                emptyMessage={
                  mcpServersQuery.isError ? "无法加载 MCP 服务器。" : "暂无 MCP 服务器。"
                }
                loading={mcpServersQuery.isPending}
              />
            )}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="绑定">
        <Controller
          control={form.control}
          name="agents"
          render={({ field }) => <AgentsField value={field.value} onChange={field.onChange} />}
        />
        <Controller
          control={form.control}
          name="enabled"
          render={({ field }) => <EnabledField checked={field.value} onChange={field.onChange} />}
        />
      </FormDialogSection>
    </FormDialog>
  );
}
