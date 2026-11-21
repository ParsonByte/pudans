import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { format } from "date-fns";
import { Users } from "lucide-react";
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
import { toDate } from "@/lib/date";
import { agentLabel, useAgentsQuery } from "@/modules/workspace/ai/library/lib/agents";
import { resolveLibraryApiError } from "@/modules/workspace/ai/library/lib/api";
import { AI_API_VERSION } from "@/modules/workspace/ai/providers/lib/api";
import {
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "@/modules/workspace/ai/providers/lib/model";

import {
  AI_TEAM_KIND,
  type AiTeamItem,
  type AiTeamQuota,
  type AiTeamSpec,
  type AiTeamStatus,
  createAiTeam,
  updateAiTeam,
} from "../lib/api";
import { DEFAULT_TEAM_NAME, TEAM_STATUS_OPTIONS, teamDisplayName } from "../lib/model";

const NO_AGENT_VALUE = "__none__";

const TEAM_STATUS_VALUES = TEAM_STATUS_OPTIONS.map((option) => option.value) as [
  AiTeamStatus,
  ...AiTeamStatus[],
];

const optionalDecimal = z
  .string()
  .trim()
  .refine(
    (value) => value === "" || (/^\d+(\.\d+)?$/.test(value) && Number(value) > 0),
    "请输入大于 0 的额度。",
  );

const optionalPositiveInt = z
  .string()
  .trim()
  .refine(
    (value) => value === "" || (/^\d+$/.test(value) && Number(value) >= 1),
    "窗口天数必须是正整数。",
  );

const optionalDateTime = z
  .string()
  .refine(
    (value) => value === "" || !Number.isNaN(new Date(value).getTime()),
    "请输入有效的重置时间。",
  );

const teamSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入团队 ID。")
    .max(63, "团队 ID 不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT)
    .refine((value) => value !== DEFAULT_TEAM_NAME, "默认团队名称由系统保留。"),
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "备注不能超过 500 个字符。"),
  ownerUserId: z.string().trim().min(1, "请输入负责人用户 UID。").max(128, "用户 UID 过长。"),
  status: z.enum(TEAM_STATUS_VALUES),
  defaultAgent: z.string(),
  quotaLimit: optionalDecimal,
  quotaResetsAt: optionalDateTime,
  quotaWindowDays: optionalPositiveInt,
});

type TeamFormValues = z.infer<typeof teamSchema>;

type TeamDialogProps = {
  team?: AiTeamItem;
  teams: AiTeamItem[];
  onClose: () => void;
};

function toFormValues(team: AiTeamItem | undefined): TeamFormValues {
  const resetsAt = toDate(team?.spec.quota?.resetsAt);
  return {
    name: team?.metadata.name ?? "",
    displayName: team?.spec.displayName ?? "",
    description: team?.spec.description ?? "",
    ownerUserId: team?.spec.ownerUserId ?? "",
    status: team?.spec.status ?? "ACTIVE",
    defaultAgent: team?.spec.defaultAgent ?? "",
    quotaLimit: team?.spec.quota?.limit != null ? String(team.spec.quota.limit) : "",
    quotaResetsAt: resetsAt ? format(resetsAt, "yyyy-MM-dd'T'HH:mm") : "",
    quotaWindowDays: team?.spec.quota?.windowDays != null ? String(team.spec.quota.windowDays) : "",
  };
}

function toSpec(values: TeamFormValues, team: AiTeamItem | undefined): AiTeamSpec {
  const quota: AiTeamQuota = {
    ...(team?.spec.quota ?? {}),
    limit: values.quotaLimit ? Number(values.quotaLimit) : undefined,
    resetsAt: values.quotaResetsAt ? new Date(values.quotaResetsAt).toISOString() : undefined,
    windowDays: values.quotaWindowDays ? Number(values.quotaWindowDays) : undefined,
  };
  return {
    ...(team?.spec ?? {}),
    displayName: values.displayName.trim(),
    description: values.description.trim() || undefined,
    ownerUserId: values.ownerUserId.trim(),
    status: values.status,
    defaultAgent: values.defaultAgent || undefined,
    members: team?.spec.members ?? [],
    quota,
    metadata: team?.spec.metadata ?? {},
  };
}

export function TeamDialog({ team, teams, onClose }: TeamDialogProps) {
  const queryClient = useQueryClient();
  const agentsQuery = useAgentsQuery();
  const form = useForm<TeamFormValues>({
    resolver: zodResolver(teamSchema) as Resolver<TeamFormValues>,
    defaultValues: toFormValues(team),
  });

  const agentOptions = (agentsQuery.data ?? []).map((agent) => ({
    value: agent.metadata.name,
    label: agentLabel(agent),
  }));
  const currentAgent = team?.spec.defaultAgent;
  if (currentAgent && !agentOptions.some((option) => option.value === currentAgent)) {
    agentOptions.push({ value: currentAgent, label: `${currentAgent}（未找到）` });
  }

  const saveMutation = useMutation({
    mutationFn: (values: TeamFormValues) => {
      const spec = toSpec(values, team);
      if (team) {
        return updateAiTeam({ ...team, spec });
      }
      return createAiTeam({
        apiVersion: AI_API_VERSION,
        kind: AI_TEAM_KIND,
        metadata: { name: values.name.trim() },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-teams"] });
      toast.success(team ? "团队已保存。" : "团队已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "团队")),
  });

  const onSubmit = form.handleSubmit(
    (values) => {
      const duplicate = teams.some(
        (item) =>
          item.metadata.name === values.name.trim() && item.metadata.name !== team?.metadata.name,
      );
      if (duplicate) {
        toast.error("团队 ID 已存在，请更换后重试。");
        return;
      }
      saveMutation.mutate(values);
    },
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Users className="size-5" />}
      title={team ? `编辑 ${teamDisplayName(team)}` : "新建团队"}
      description="团队决定用户可以使用哪些 Agent 与对话资源；成员通过独立接口维护。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-3xl"
      submitLabel={team ? "保存更改" : "创建团队"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="团队 ID"
            htmlFor="team-name"
            hint={team ? "团队 ID 创建后不可修改。" : RESOURCE_NAME_HINT}
            error={form.formState.errors.name?.message}
          >
            <Input
              id="team-name"
              placeholder="例如：product-research"
              disabled={Boolean(team)}
              {...form.register("name")}
            />
          </FormField>
          <FormField
            label="名称"
            htmlFor="team-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="team-display-name"
              placeholder="例如：产品研究组"
              {...form.register("displayName")}
            />
          </FormField>
        </div>
        <FormField
          label="备注"
          htmlFor="team-description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            id="team-description"
            rows={3}
            placeholder="说明团队用途、可使用的资源或协作范围。"
            {...form.register("description")}
          />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="负责人用户 UID"
            htmlFor="team-owner"
            hint="必须是已存在的用户 UID；系统内置团队可使用 system。"
            error={form.formState.errors.ownerUserId?.message}
          >
            <Input id="team-owner" placeholder="用户 UID" {...form.register("ownerUserId")} />
          </FormField>
          <FormField
            label="团队状态"
            htmlFor="team-status"
            error={form.formState.errors.status?.message}
          >
            <Controller
              control={form.control}
              name="status"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as AiTeamStatus)}
                >
                  <SelectTrigger id="team-status" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TEAM_STATUS_OPTIONS.map((option) => (
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
      </FormDialogSection>

      <FormDialogSection title="默认 Agent">
        <FormField
          label="默认 Agent"
          htmlFor="team-default-agent"
          hint={
            agentsQuery.isError
              ? "无法加载 Agent 列表，可保留原值或暂不指定。"
              : "成员未指定 Agent 时使用该默认值。"
          }
          error={form.formState.errors.defaultAgent?.message}
        >
          <Controller
            control={form.control}
            name="defaultAgent"
            render={({ field }) => (
              <Select
                value={field.value || NO_AGENT_VALUE}
                disabled={agentsQuery.isPending}
                onValueChange={(value) => field.onChange(value === NO_AGENT_VALUE ? "" : value)}
              >
                <SelectTrigger id="team-default-agent" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_AGENT_VALUE}>不指定</SelectItem>
                  {agentOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="配额">
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField
            label="额度上限"
            htmlFor="team-quota-limit"
            hint="留空表示不限制。"
            error={form.formState.errors.quotaLimit?.message}
          >
            <Input
              id="team-quota-limit"
              type="number"
              min={0}
              step="any"
              {...form.register("quotaLimit")}
            />
          </FormField>
          <FormField
            label="窗口天数"
            htmlFor="team-quota-window"
            hint="额度按该窗口周期重置。"
            error={form.formState.errors.quotaWindowDays?.message}
          >
            <Input
              id="team-quota-window"
              type="number"
              min={1}
              step={1}
              {...form.register("quotaWindowDays")}
            />
          </FormField>
          <FormField
            label="下次重置时间"
            htmlFor="team-quota-resets-at"
            hint="留空表示由后端按窗口计算。"
            error={form.formState.errors.quotaResetsAt?.message}
          >
            <Input
              id="team-quota-resets-at"
              type="datetime-local"
              {...form.register("quotaResetsAt")}
            />
          </FormField>
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}
