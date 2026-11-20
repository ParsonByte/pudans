import { useState } from "react";
import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ChevronDown,
  ChevronUp,
  ListChecks,
  Loader2,
  Plus,
  Search,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
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

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import {
  ProviderDialogSection,
  ProviderDialogShell,
  ProviderFieldRow,
} from "../../providers/components/provider-dialog-shell";
import {
  AI_API_VERSION,
  AI_ROUTE_GROUP_KIND,
  type AiModelItem,
  type AiProviderAccountItem,
  type AiProviderAffinity,
  type AiProviderItem,
  type AiRouteGroupContextMode,
  type AiRouteGroupItem,
  type AiRouteGroupMember,
  type AiRouteGroupMode,
  type AiRouteGroupParentMode,
  type AiRouteGroupRule,
  type AiRouteGroupTier,
  createRouteGroup,
  resolveAiProviderApiError,
  updateRouteGroup,
} from "../../providers/lib/api";
import {
  AFFINITY_OPTIONS,
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
  modelDisplayName,
} from "../../providers/lib/model";
import { formatTokenCount } from "../../routing/lib/model";
import {
  ROUTE_GROUP_CONTEXT_MODE_OPTIONS,
  ROUTE_GROUP_PARENT_MODE_OPTIONS,
  ROUTE_GROUP_TIER_OPTIONS,
  ROUTE_MODE_OPTIONS,
  type RouteTemplate,
  affinityLabel,
  builtinParentMode,
  effectiveContextMode,
  effectiveParentMode,
  memberDrafts,
  parentModeLabel,
  routeGroupRules,
  uniqueGroupName,
  usableModels,
} from "../lib/model";

const ROUTE_MODE_VALUES = ROUTE_MODE_OPTIONS.map((option) => option.value) as [
  AiRouteGroupMode,
  ...AiRouteGroupMode[],
];

const AFFINITY_VALUES = AFFINITY_OPTIONS.map((option) => option.value) as [
  AiProviderAffinity,
  ...AiProviderAffinity[],
];

const TIER_VALUES = ROUTE_GROUP_TIER_OPTIONS.map((option) => option.value) as [
  AiRouteGroupTier,
  ...AiRouteGroupTier[],
];

const PARENT_MODE_VALUES = ROUTE_GROUP_PARENT_MODE_OPTIONS.map((option) => option.value) as [
  AiRouteGroupParentMode,
  ...AiRouteGroupParentMode[],
];

const CONTEXT_MODE_VALUES = ROUTE_GROUP_CONTEXT_MODE_OPTIONS.map((option) => option.value) as [
  AiRouteGroupContextMode,
  ...AiRouteGroupContextMode[],
];

const routeGroupSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入资源 ID。")
    .max(63, "资源 ID 不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "备注不能超过 500 个字符。"),
  routeMode: z.enum(ROUTE_MODE_VALUES),
  defaultTier: z.enum(TIER_VALUES),
  parentMode: z.enum(PARENT_MODE_VALUES),
  qualityThreshold: z.coerce.number().min(0, "质量阈值不能小于 0。").max(1, "质量阈值不能大于 1。"),
  order: z.coerce.number().int().min(0, "排序不能为负数。").max(100000),
  affinity: z.enum(AFFINITY_VALUES),
  contextMode: z.enum(CONTEXT_MODE_VALUES),
  patienceSeconds: z.coerce.number().int().min(0, "等待时间不能为负数。").max(86400),
  firstTokenSeconds: z.coerce.number().int().min(0, "首字时间不能为负数。").max(86400),
  sink: z.boolean(),
  enabled: z.boolean(),
});

type RouteGroupFormValues = z.infer<typeof routeGroupSchema>;

type MemberDraft = {
  model: string;
  weight: number;
  tierOverride: AiRouteGroupTier;
  capabilityTags: string;
  extraContextMargin: number;
  enabled: boolean;
};

type RuleDraft = {
  name: string;
  condition: string;
  member: string;
  priority: number;
  agents: string;
  tokensGte: string;
  imagesGte: string;
  pause: boolean;
  use: string;
};

type RouteGroupDialogProps = {
  group?: AiRouteGroupItem;
  template?: RouteTemplate;
  routeGroups: AiRouteGroupItem[];
  models: AiModelItem[];
  providers: AiProviderItem[];
  accounts: AiProviderAccountItem[];
  onClose: () => void;
  onSaved: (group: AiRouteGroupItem) => void;
};

export function RouteGroupDialog({
  group,
  template,
  routeGroups,
  models,
  providers,
  accounts,
  onClose,
  onSaved,
}: RouteGroupDialogProps) {
  const queryClient = useQueryClient();
  const existingNames = new Set(
    routeGroups
      .filter((item) => item.metadata.name !== group?.metadata.name)
      .map((item) => item.metadata.name),
  );
  const [members, setMembers] = useState<MemberDraft[]>(initialMembers(group, template));
  const [manualPick, setManualPick] = useState(group?.spec.manualPick ?? "");
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerSearch, setPickerSearch] = useState("");
  const [fallbackGroups, setFallbackGroups] = useState<string[]>(group?.spec.fallback ?? []);
  const [selectedAccounts, setSelectedAccounts] = useState<string[]>(group?.spec.accounts ?? []);
  const [rules, setRules] = useState<RuleDraft[]>(initialRules(group));

  const form = useForm<RouteGroupFormValues>({
    resolver: zodResolver(routeGroupSchema) as Resolver<RouteGroupFormValues>,
    defaultValues: {
      name:
        group?.metadata.name ??
        uniqueGroupName(template ? `${template.id}-group` : "route-group", existingNames),
      displayName: group?.spec.displayName ?? template?.title ?? "",
      description: group?.spec.description ?? template?.description ?? "",
      routeMode: group?.spec.routeMode ?? template?.mode ?? "SMART",
      defaultTier: group?.spec.defaultTier ?? "AUTO",
      parentMode: group ? effectiveParentMode(group) : "NONE",
      qualityThreshold: group?.spec.qualityThreshold ?? 0.6,
      order: group?.spec.order ?? 0,
      affinity: group?.spec.affinity ?? "AUTO",
      contextMode: effectiveContextMode(group?.spec.contextMode),
      patienceSeconds: group?.spec.patienceSeconds ?? 60,
      firstTokenSeconds: group?.spec.firstTokenSeconds ?? 0,
      sink: group?.spec.sink ?? false,
      enabled: group?.spec.enabled !== false,
    },
  });
  const routeMode = useWatch({ control: form.control, name: "routeMode" });
  const enabled = useWatch({ control: form.control, name: "enabled" });
  const defaultTier = useWatch({ control: form.control, name: "defaultTier" });
  const affinity = useWatch({ control: form.control, name: "affinity" });
  const configuredParentMode = useWatch({ control: form.control, name: "parentMode" });
  const contextMode = useWatch({ control: form.control, name: "contextMode" });
  const sink = useWatch({ control: form.control, name: "sink" });
  const nameValue = useWatch({ control: form.control, name: "name" });
  const forcedParentMode = builtinParentMode(nameValue);
  const parentMode = forcedParentMode ?? configuredParentMode;

  const usable = usableModels(models, providers, accounts);
  const selectedModels = new Set(members.map((member) => member.model));
  const accountOptions = accounts.map((account) => {
    const provider = providers.find((item) => item.metadata.name === account.spec.provider);
    return {
      value: account.metadata.name,
      label: account.spec.displayName || account.metadata.name,
      hint: [
        provider?.spec.displayName || account.spec.provider,
        account.spec.keyName,
        account.spec.enabled === false ? "已停用" : "",
      ]
        .filter(Boolean)
        .join(" · "),
    };
  });
  const knownGroupNames = new Set(routeGroups.map((item) => item.metadata.name));
  const unknownFallback = (group?.spec.fallback ?? []).filter((name) => !knownGroupNames.has(name));
  const fallbackOptions = routeGroups.filter((item) => item.metadata.name !== group?.metadata.name);
  const keyword = pickerSearch.trim().toLowerCase();
  const selectableModels = usable.filter((model) => {
    if (selectedModels.has(model.metadata.name)) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    const provider = providers.find((item) => item.metadata.name === model.spec.provider);
    return `${modelDisplayName(model)} ${model.spec.modelCode} ${provider?.spec.displayName ?? ""}`
      .toLowerCase()
      .includes(keyword);
  });
  const selectableGroups = providers
    .map((provider) => ({
      provider,
      models: selectableModels.filter((model) => model.spec.provider === provider.metadata.name),
    }))
    .filter((entry) => entry.models.length > 0);

  const mutation = useMutation({
    mutationFn: (payload: AiRouteGroupItem) =>
      group ? updateRouteGroup(payload) : createRouteGroup(payload),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-route-groups"] });
      toast.success(group ? "路由组已更新。" : "路由组已创建。");
      onSaved(saved);
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const addMember = (model: AiModelItem) => {
    setMembers((current) => [
      ...current,
      {
        model: model.metadata.name,
        weight: 1,
        tierOverride: "AUTO",
        capabilityTags: "",
        extraContextMargin: 1024,
        enabled: true,
      },
    ]);
    setPickerSearch("");
    setPickerOpen(false);
  };

  const updateMember = (index: number, patch: Partial<MemberDraft>) => {
    setMembers((current) =>
      current.map((member, currentIndex) =>
        currentIndex === index ? { ...member, ...patch } : member,
      ),
    );
  };

  const moveMember = (index: number, direction: -1 | 1) => {
    setMembers((current) => {
      const target = index + direction;
      if (target < 0 || target >= current.length) {
        return current;
      }
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const removeMember = (index: number) => {
    const removed = members[index];
    setMembers((current) => current.filter((_, currentIndex) => currentIndex !== index));
    if (removed && manualPick === removed.model) {
      setManualPick("");
    }
    if (removed) {
      setRules((current) =>
        current.map((rule) => (rule.member === removed.model ? { ...rule, member: "" } : rule)),
      );
    }
  };

  const addRule = () => {
    setRules((current) => [
      ...current,
      {
        name: "",
        condition: "",
        member: members.find((member) => member.enabled)?.model ?? members[0]?.model ?? "",
        priority: 0,
        agents: "",
        tokensGte: "",
        imagesGte: "",
        pause: false,
        use: "",
      },
    ]);
  };

  const updateRule = (index: number, patch: Partial<RuleDraft>) => {
    setRules((current) =>
      current.map((rule, currentIndex) => (currentIndex === index ? { ...rule, ...patch } : rule)),
    );
  };

  const removeRule = (index: number) => {
    setRules((current) => current.filter((_, currentIndex) => currentIndex !== index));
  };

  const toggleFallback = (name: string, checked: boolean) => {
    setFallbackGroups((current) =>
      checked ? [...new Set([...current, name])] : current.filter((item) => item !== name),
    );
  };

  const submit = form.handleSubmit((values) => {
    if (existingNames.has(values.name)) {
      form.setError("name", { message: "资源 ID 已存在，请更换。" });
      return;
    }
    if (members.length === 0) {
      toast.error("请至少添加一个模型成员。");
      return;
    }
    const payloadMembers: AiRouteGroupMember[] = members.map((member) => ({
      model: member.model,
      weight: Math.min(1000, Math.max(1, Math.round(member.weight) || 1)),
      capabilityTags: splitValues(member.capabilityTags),
      extraContextMargin: Number.isFinite(member.extraContextMargin)
        ? Math.max(0, Math.round(member.extraContextMargin))
        : 1024,
      enabled: member.enabled,
      ...(member.tierOverride === "AUTO" ? {} : { tierOverride: member.tierOverride }),
    }));
    const payloadRules: AiRouteGroupRule[] = [];
    for (const rule of rules) {
      const name = rule.name.trim();
      const condition = rule.condition.trim();
      const member = rule.member.trim();
      const use = rule.use.trim();
      const agents = splitValues(rule.agents);
      const tokensGte = parseOptionalCount(rule.tokensGte);
      const imagesGte = parseOptionalCount(rule.imagesGte);
      const hasSelector =
        Boolean(condition) ||
        agents.length > 0 ||
        tokensGte !== undefined ||
        imagesGte !== undefined;
      if (!name && !hasSelector && !member && !use) {
        continue;
      }
      const target = use || member;
      if (!target) {
        toast.error("每条规则都需要指定成员或覆盖成员。");
        return;
      }
      if (!selectedModels.has(target)) {
        toast.error("规则的成员与覆盖成员必须是当前模型成员。");
        return;
      }
      if (!hasSelector) {
        toast.error("规则至少需要条件、智能体、Token 阈值或图片阈值之一。");
        return;
      }
      payloadRules.push({
        name: name || undefined,
        condition: condition || undefined,
        member: member || undefined,
        priority: Math.max(0, Math.round(rule.priority) || 0),
        agents,
        tokensGte,
        imagesGte,
        pause: rule.pause,
        use: use || undefined,
      });
    }
    const payload: AiRouteGroupItem = {
      apiVersion: AI_API_VERSION,
      kind: AI_ROUTE_GROUP_KIND,
      metadata: {
        ...(group?.metadata ?? {}),
        name: values.name,
      },
      spec: {
        ...(group?.spec ?? {}),
        displayName: values.displayName,
        description: values.description || undefined,
        routeMode: values.routeMode,
        defaultTier: values.defaultTier,
        parentMode: forcedParentMode ?? values.parentMode,
        qualityThreshold: values.qualityThreshold,
        affinity: values.affinity,
        fallback: [...new Set([...unknownFallback, ...fallbackGroups])],
        accounts: selectedAccounts,
        order: values.order,
        contextMode: values.contextMode,
        patienceSeconds: values.patienceSeconds,
        firstTokenSeconds: values.firstTokenSeconds,
        sink: values.sink,
        rules: payloadRules,
        enabled: values.enabled,
        isDefault: group?.spec.isDefault ?? false,
        manualPick:
          values.routeMode === "MANUAL"
            ? members.some((member) => member.model === manualPick)
              ? manualPick
              : payloadMembers[0]?.model
            : undefined,
        members: payloadMembers,
      },
    };
    mutation.mutate(payload);
  });

  return (
    <ProviderDialogShell
      title={group ? "编辑路由组" : "新建组"}
      description="路由组决定网关可以选用哪些模型，以及它们按什么顺序接请求。"
      icon={<SlidersHorizontal className="size-4" />}
      onSubmit={submit}
      onClose={onClose}
      isPending={mutation.isPending}
      submitLabel={group ? "保存" : "创建"}
      className="sm:max-w-3xl"
    >
      <ProviderDialogSection title="基本信息">
        <ProviderFieldRow label="名称" error={form.formState.errors.displayName?.message}>
          <Input
            placeholder="例如：Opus 多家备份"
            aria-invalid={Boolean(form.formState.errors.displayName)}
            {...form.register("displayName")}
          />
        </ProviderFieldRow>
        <ProviderFieldRow
          label="资源 ID"
          hint={group ? "资源 ID 创建后不可修改。" : RESOURCE_NAME_HINT}
          error={form.formState.errors.name?.message}
        >
          <Input
            disabled={Boolean(group)}
            aria-invalid={Boolean(form.formState.errors.name)}
            {...form.register("name")}
          />
        </ProviderFieldRow>
        <ProviderFieldRow
          label="备注"
          hint="可选，只用于说明这个组的用途。"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            rows={2}
            placeholder="例如：给工作 Key 用。"
            {...form.register("description")}
          />
        </ProviderFieldRow>
        <ProviderFieldRow
          label="所属模式"
          hint={
            forcedParentMode
              ? "内置固定组由名称决定所属模式，不能修改。"
              : "快速或进阶模式会列出同模式的自定义组；不关联的组只能由网关显式指定。"
          }
        >
          <Select
            value={parentMode}
            disabled={Boolean(forcedParentMode)}
            onValueChange={(value) =>
              form.setValue("parentMode", value as AiRouteGroupParentMode, { shouldDirty: true })
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ROUTE_GROUP_PARENT_MODE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </ProviderFieldRow>
        <ProviderFieldRow label="启用" hint="停用后，Agent 不会从这个组选择模型。">
          <Switch
            checked={enabled}
            onCheckedChange={(checked) => form.setValue("enabled", checked)}
            aria-label="启用路由组"
          />
        </ProviderFieldRow>
      </ProviderDialogSection>

      <ProviderDialogSection title="路由方式">
        <ProviderFieldRow label="路由方式">
          <Select
            value={routeMode}
            onValueChange={(value) =>
              form.setValue("routeMode", value as AiRouteGroupMode, { shouldDirty: true })
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ROUTE_MODE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </ProviderFieldRow>
        <ProviderFieldRow label="默认等级" hint="智能路由按等级筛选；「继承」跟随请求或对话模式。">
          <Select
            value={defaultTier}
            onValueChange={(value) =>
              form.setValue("defaultTier", value as AiRouteGroupTier, { shouldDirty: true })
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ROUTE_GROUP_TIER_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </ProviderFieldRow>
        <ProviderFieldRow label="亲和" hint="同一会话或轮次尽量复用上次选中的账号。">
          <Select
            value={affinity}
            onValueChange={(value) =>
              form.setValue("affinity", value as AiProviderAffinity, { shouldDirty: true })
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {AFFINITY_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </ProviderFieldRow>
        {routeMode === "MANUAL" ? (
          <ProviderFieldRow label="手动选择" hint="手动模式固定使用这个成员。">
            <Select
              value={manualPick || members[0]?.model || ""}
              onValueChange={setManualPick}
              disabled={members.length === 0}
            >
              <SelectTrigger>
                <SelectValue placeholder={members.length === 0 ? "请先添加成员" : "选择成员"} />
              </SelectTrigger>
              <SelectContent>
                {members.map((member) => {
                  const model = models.find((item) => item.metadata.name === member.model);
                  return (
                    <SelectItem key={member.model} value={member.model}>
                      {model ? modelDisplayName(model) : member.model}
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          </ProviderFieldRow>
        ) : null}
      </ProviderDialogSection>

      <ProviderDialogSection title="高级设置">
        <ProviderFieldRow
          label="上下文选择"
          hint={
            ROUTE_GROUP_CONTEXT_MODE_OPTIONS.find((option) => option.value === contextMode)?.hint
          }
        >
          <Select
            value={contextMode}
            onValueChange={(value) =>
              form.setValue("contextMode", value as AiRouteGroupContextMode, {
                shouldDirty: true,
              })
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ROUTE_GROUP_CONTEXT_MODE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </ProviderFieldRow>
        <div className="grid gap-4 sm:grid-cols-2">
          <ProviderFieldRow
            label="等待时间"
            hint="秒；候选响应过慢时等待的时长，0 表示不额外等待。"
          >
            <Input type="number" min={0} {...form.register("patienceSeconds")} />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="首字时间"
            hint="秒；首字在此时间内到达才继续等待，0 表示不限制。"
          >
            <Input type="number" min={0} {...form.register("firstTokenSeconds")} />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="质量阈值"
            hint="0 到 1；候选健康度低于阈值时会被排除，0 表示不按质量排除。"
            error={form.formState.errors.qualityThreshold?.message}
          >
            <Input
              type="number"
              min={0}
              max={1}
              step={0.05}
              {...form.register("qualityThreshold")}
            />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="排序"
            hint="数字越小越靠前；也可在列表中用箭头调整。"
            error={form.formState.errors.order?.message}
          >
            <Input type="number" min={0} {...form.register("order")} />
          </ProviderFieldRow>
        </div>
        <ProviderFieldRow label="Sink" hint="开启后，被限流的账号会在冷却期内从候选里排除。">
          <Switch
            checked={sink}
            onCheckedChange={(checked) => form.setValue("sink", checked, { shouldDirty: true })}
            aria-label="Sink"
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="后备组" hint="当前组无法选出候选时，按顺序尝试这些路由组。">
          <div className="max-h-44 space-y-1 overflow-y-auto rounded-lg border p-2">
            {fallbackOptions.length === 0 ? (
              <p className="px-2 py-3 text-xs text-muted-foreground">没有其他路由组可选。</p>
            ) : (
              fallbackOptions.map((option) => (
                <label
                  key={option.metadata.name}
                  className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-accent/40"
                >
                  <Checkbox
                    checked={fallbackGroups.includes(option.metadata.name)}
                    onCheckedChange={(checked) =>
                      toggleFallback(option.metadata.name, checked === true)
                    }
                  />
                  <span className="min-w-0 flex-1 truncate">
                    {option.spec.displayName || option.metadata.name}
                  </span>
                  <span className="shrink-0 text-[10px] text-muted-foreground">
                    {parentModeLabel(effectiveParentMode(option))}
                  </span>
                </label>
              ))
            )}
          </div>
          {unknownFallback.length > 0 ? (
            <p className="text-[11px] text-muted-foreground">
              已保留 {unknownFallback.length} 个当前不存在的后备引用。
            </p>
          ) : null}
        </ProviderFieldRow>
        <ProviderFieldRow label="限制账号" hint="留空表示不限制；选择后只有这些账号参与本组路由。">
          <MultiSelect
            value={selectedAccounts}
            onChange={setSelectedAccounts}
            options={accountOptions}
            placeholder="未限制账号"
            emptyMessage={accounts.length === 0 ? "还没有供应商账号。" : "没有匹配的账号。"}
            searchPlaceholder="搜索账号"
          />
          {selectedAccounts.some(
            (name) => !accounts.some((account) => account.metadata.name === name),
          ) ? (
            <p className="text-[11px] text-muted-foreground">
              已保留当前不存在的账号引用，保存后会继续生效。
            </p>
          ) : null}
        </ProviderFieldRow>
      </ProviderDialogSection>

      <ProviderDialogSection
        title={
          <span className="flex items-center justify-between gap-2">
            <span>模型成员</span>
            <span className="text-xs font-normal text-muted-foreground">{members.length} 个</span>
          </span>
        }
      >
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs text-muted-foreground">按当前顺序尝试；权重用于权重模式。</p>
          <Popover open={pickerOpen} onOpenChange={setPickerOpen}>
            <PopoverTrigger asChild>
              <Button type="button" variant="outline" size="sm">
                <Plus />
                添加模型
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-[380px] p-0">
              <div className="flex items-center gap-2 border-b px-3 py-2">
                <Search className="size-4 shrink-0 text-muted-foreground" />
                <Input
                  value={pickerSearch}
                  onChange={(event) => setPickerSearch(event.target.value)}
                  placeholder="筛选，或输入模型名称…"
                  className="h-7 border-0 px-0 shadow-none focus-visible:ring-0"
                  autoFocus
                />
              </div>
              <div className="max-h-80 overflow-y-auto p-2">
                {selectableGroups.length === 0 ? (
                  <p className="px-2 py-6 text-center text-xs text-muted-foreground">
                    没有可添加的模型。先在供应商里添加账号和模型。
                  </p>
                ) : (
                  selectableGroups.map(({ provider, models: providerModels }) => (
                    <div key={provider.metadata.name} className="mb-2 last:mb-0">
                      <div className="flex items-center gap-1.5 px-2 py-1 text-[10px] font-medium tracking-wide text-muted-foreground uppercase">
                        <ProviderBrandIcon provider={provider} className="size-3" />
                        {provider.spec.displayName || provider.metadata.name}
                      </div>
                      {providerModels.map((model) => (
                        <button
                          key={model.metadata.name}
                          type="button"
                          onClick={() => addMember(model)}
                          className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-start transition-colors hover:bg-accent"
                        >
                          <ProviderBrandIcon provider={provider} className="size-4" />
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm">
                              {modelDisplayName(model)}
                            </span>
                            <span className="block truncate font-mono text-[10px] text-muted-foreground">
                              {model.spec.modelCode}
                            </span>
                          </span>
                          <span className="shrink-0 text-[10px] text-muted-foreground">
                            {model.spec.contextWindow
                              ? `${formatTokenCount(model.spec.contextWindow)} token`
                              : "未设上下文"}
                          </span>
                        </button>
                      ))}
                    </div>
                  ))
                )}
              </div>
            </PopoverContent>
          </Popover>
        </div>

        {members.length === 0 ? (
          <div className="flex min-h-24 items-center justify-center rounded-xl border border-dashed px-4 py-6 text-center text-xs text-muted-foreground">
            还没有成员。添加模型后，网关才能从这个组选择。
          </div>
        ) : (
          <div className="space-y-2">
            {members.map((member, index) => {
              const model = models.find((item) => item.metadata.name === member.model);
              const provider = model
                ? providers.find((item) => item.metadata.name === model.spec.provider)
                : undefined;
              return (
                <div key={member.model} className="rounded-xl border bg-background/40 p-3">
                  <div className="flex items-center gap-2">
                    <ProviderBrandIcon provider={provider} className="size-4" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">
                        {model ? modelDisplayName(model) : member.model}
                      </p>
                      <p className="truncate text-[10px] text-muted-foreground">
                        {provider?.spec.displayName || model?.spec.provider || "未知供应商"}
                        {model ? ` · ${model.spec.modelCode}` : ""}
                      </p>
                    </div>
                    <div className="flex items-center gap-0.5">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-xs"
                        onClick={() => moveMember(index, -1)}
                        disabled={index === 0}
                        aria-label="上移"
                      >
                        <ChevronUp />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-xs"
                        onClick={() => moveMember(index, 1)}
                        disabled={index === members.length - 1}
                        aria-label="下移"
                      >
                        <ChevronDown />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-xs"
                        onClick={() => removeMember(index)}
                        aria-label="移除"
                      >
                        <X />
                      </Button>
                    </div>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-3 ps-6">
                    <label className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                      权重
                      <Input
                        type="number"
                        min={1}
                        max={1000}
                        value={member.weight}
                        onChange={(event) =>
                          updateMember(index, {
                            weight: Number(event.target.value) || 1,
                          })
                        }
                        className="h-7 w-16"
                      />
                    </label>
                    <label className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                      等级
                      <Select
                        value={member.tierOverride}
                        onValueChange={(value) =>
                          updateMember(index, {
                            tierOverride: value as AiRouteGroupTier,
                          })
                        }
                      >
                        <SelectTrigger className="h-7 w-24">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {ROUTE_GROUP_TIER_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </label>
                    <label className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                      启用
                      <Switch
                        size="sm"
                        checked={member.enabled}
                        onCheckedChange={(checked) => updateMember(index, { enabled: checked })}
                        aria-label="启用成员"
                      />
                    </label>
                  </div>
                  <div className="mt-2 grid gap-2 ps-6 sm:grid-cols-[minmax(0,1fr)_150px]">
                    <label className="space-y-1 text-[11px] text-muted-foreground">
                      能力标签
                      <Input
                        value={member.capabilityTags}
                        onChange={(event) =>
                          updateMember(index, { capabilityTags: event.target.value })
                        }
                        placeholder="例如 vision, tools"
                        className="h-7"
                      />
                    </label>
                    <label className="space-y-1 text-[11px] text-muted-foreground">
                      额外上下文
                      <Input
                        type="number"
                        min={0}
                        value={member.extraContextMargin}
                        onChange={(event) =>
                          updateMember(index, {
                            extraContextMargin: Number(event.target.value) || 0,
                          })
                        }
                        className="h-7"
                        aria-label="额外上下文"
                      />
                    </label>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </ProviderDialogSection>

      <ProviderDialogSection
        title={
          <span className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5">
              <ListChecks className="size-4" />
              路由规则
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={addRule}
              disabled={members.length === 0}
            >
              <Plus />
              添加规则
            </Button>
          </span>
        }
      >
        <p className="text-xs leading-5 text-muted-foreground">
          条件语法：model:xxx、tier:HIGH、capability:VISION、context&gt;=N、provider:xxx；多个子句用逗号分隔，全部满足才命中。智能体、Token
          阈值和图片阈值属于结构化条件，可与条件同时使用。优先级高的规则先匹配，暂停的规则不参与匹配。
        </p>
        {rules.length === 0 ? (
          <div className="flex min-h-20 items-center justify-center rounded-xl border border-dashed px-4 py-5 text-center text-xs text-muted-foreground">
            {members.length === 0
              ? "先添加模型成员，再配置规则。"
              : "还没有规则。添加规则后，满足条件的请求会优先选择指定成员。"}
          </div>
        ) : (
          <div className="space-y-2">
            {rules.map((rule, index) => (
              <div key={index} className="space-y-2 rounded-xl border bg-background/40 p-3">
                <div className="flex items-center gap-2">
                  <Input
                    value={rule.name}
                    onChange={(event) => updateRule(index, { name: event.target.value })}
                    placeholder="规则名称（可选）"
                    className="h-8 flex-1"
                  />
                  <Input
                    type="number"
                    min={0}
                    value={rule.priority}
                    onChange={(event) =>
                      updateRule(index, { priority: Number(event.target.value) || 0 })
                    }
                    placeholder="优先级"
                    className="h-8 w-24"
                    aria-label="规则优先级"
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-xs"
                    onClick={() => removeRule(index)}
                    aria-label="删除规则"
                  >
                    <X />
                  </Button>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  <label className="space-y-1 text-[11px] text-muted-foreground">
                    条件
                    <Input
                      value={rule.condition}
                      onChange={(event) => updateRule(index, { condition: event.target.value })}
                      placeholder="例如 capability:VISION,tier:HIGH"
                      className="h-8"
                    />
                  </label>
                  <label className="space-y-1 text-[11px] text-muted-foreground">
                    成员
                    <Select
                      value={rule.member || undefined}
                      onValueChange={(value) => updateRule(index, { member: value })}
                    >
                      <SelectTrigger className="h-8">
                        <SelectValue placeholder="选择成员" />
                      </SelectTrigger>
                      <SelectContent>
                        {members.map((member) => {
                          const model = models.find((item) => item.metadata.name === member.model);
                          return (
                            <SelectItem key={member.model} value={member.model}>
                              {model ? modelDisplayName(model) : member.model}
                            </SelectItem>
                          );
                        })}
                      </SelectContent>
                    </Select>
                  </label>
                </div>
                <div className="grid gap-2 sm:grid-cols-3">
                  <label className="space-y-1 text-[11px] text-muted-foreground">
                    智能体
                    <Input
                      value={rule.agents}
                      onChange={(event) => updateRule(index, { agents: event.target.value })}
                      placeholder="agent-a, agent-b"
                      className="h-8"
                    />
                  </label>
                  <label className="space-y-1 text-[11px] text-muted-foreground">
                    Token 阈值
                    <Input
                      type="number"
                      min={0}
                      value={rule.tokensGte}
                      onChange={(event) => updateRule(index, { tokensGte: event.target.value })}
                      placeholder="不低于"
                      className="h-8"
                    />
                  </label>
                  <label className="space-y-1 text-[11px] text-muted-foreground">
                    图片阈值
                    <Input
                      type="number"
                      min={0}
                      value={rule.imagesGte}
                      onChange={(event) => updateRule(index, { imagesGte: event.target.value })}
                      placeholder="不少于"
                      className="h-8"
                    />
                  </label>
                </div>
                <div className="flex flex-wrap items-end gap-4">
                  <label className="min-w-48 flex-1 space-y-1 text-[11px] text-muted-foreground">
                    覆盖成员
                    <Select
                      value={rule.use || "NONE"}
                      onValueChange={(value) =>
                        updateRule(index, { use: value === "NONE" ? "" : value })
                      }
                    >
                      <SelectTrigger className="h-8">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="NONE">不覆盖</SelectItem>
                        {members.map((member) => {
                          const model = models.find((item) => item.metadata.name === member.model);
                          return (
                            <SelectItem key={member.model} value={member.model}>
                              {model ? modelDisplayName(model) : member.model}
                            </SelectItem>
                          );
                        })}
                      </SelectContent>
                    </Select>
                  </label>
                  <label className="flex items-center gap-2 pb-1 text-[11px] text-muted-foreground">
                    <Switch
                      size="sm"
                      checked={rule.pause}
                      onCheckedChange={(checked) => updateRule(index, { pause: checked })}
                      aria-label="暂停规则"
                    />
                    暂停规则
                  </label>
                </div>
              </div>
            ))}
          </div>
        )}
      </ProviderDialogSection>

      <p className="px-1 text-[11px] text-muted-foreground">
        当前组：{parentModeLabel(parentMode)} · {affinityLabel(affinity)}亲和 ·{" "}
        {ROUTE_MODE_OPTIONS.find((option) => option.value === routeMode)?.label ?? routeMode} ·{" "}
        {rules.length} 条规则
        {mutation.isPending ? (
          <span className="ms-2 inline-flex items-center gap-1">
            <Loader2 className="size-3 animate-spin" />
            正在保存
          </span>
        ) : null}
      </p>
    </ProviderDialogShell>
  );
}

function initialRules(group: AiRouteGroupItem | undefined): RuleDraft[] {
  return routeGroupRules(group).map((rule) => ({
    name: rule.name ?? "",
    condition: rule.condition ?? "",
    member: rule.member ?? "",
    priority: rule.priority ?? 0,
    agents: (rule.agents ?? []).join(", "),
    tokensGte: rule.tokensGte == null ? "" : String(rule.tokensGte),
    imagesGte: rule.imagesGte == null ? "" : String(rule.imagesGte),
    pause: rule.pause ?? false,
    use: rule.use ?? "",
  }));
}

function initialMembers(
  group: AiRouteGroupItem | undefined,
  template: RouteTemplate | undefined,
): MemberDraft[] {
  if (group) {
    return memberDrafts(group).map((member) => ({
      model: member.model,
      weight: member.weight ?? 1,
      tierOverride: member.tierOverride ?? "AUTO",
      capabilityTags: (member.capabilityTags ?? []).join(", "),
      extraContextMargin: member.extraContextMargin ?? 1024,
      enabled: member.enabled !== false,
    }));
  }
  return (template?.models ?? []).map((model) => ({
    model: model.metadata.name,
    weight: 1,
    tierOverride: "AUTO",
    capabilityTags: "",
    extraContextMargin: 1024,
    enabled: true,
  }));
}

function splitValues(value: string): string[] {
  return [
    ...new Set(
      value
        .split(/[\s,，;；]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ];
}

function parseOptionalCount(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? Math.max(0, Math.round(parsed)) : undefined;
}
