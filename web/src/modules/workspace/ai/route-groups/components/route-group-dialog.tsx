import { useState } from "react";
import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, ChevronUp, Loader2, Plus, Search, SlidersHorizontal, X } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { Button } from "@/components/ui/button";
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
  type AiRouteGroupItem,
  type AiRouteGroupMember,
  type AiRouteGroupMode,
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
  ROUTE_GROUP_TIER_OPTIONS,
  ROUTE_MODE_OPTIONS,
  type RouteTemplate,
  affinityLabel,
  memberDrafts,
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
  affinity: z.enum(AFFINITY_VALUES),
  enabled: z.boolean(),
});

type RouteGroupFormValues = z.infer<typeof routeGroupSchema>;

type MemberDraft = {
  model: string;
  weight: number;
  tierOverride: AiRouteGroupTier;
  enabled: boolean;
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
      affinity: group?.spec.affinity ?? "AUTO",
      enabled: group?.spec.enabled !== false,
    },
  });
  const routeMode = useWatch({ control: form.control, name: "routeMode" });
  const enabled = useWatch({ control: form.control, name: "enabled" });
  const defaultTier = useWatch({ control: form.control, name: "defaultTier" });
  const affinity = useWatch({ control: form.control, name: "affinity" });

  const usable = usableModels(models, providers, accounts);
  const selectedModels = new Set(members.map((member) => member.model));
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
      enabled: member.enabled,
      ...(member.tierOverride === "AUTO" ? {} : { tierOverride: member.tierOverride }),
    }));
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
        affinity: values.affinity,
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
                </div>
              );
            })}
          </div>
        )}
      </ProviderDialogSection>

      <p className="px-1 text-[11px] text-muted-foreground">
        当前组：{affinityLabel(affinity)}亲和 ·{" "}
        {ROUTE_MODE_OPTIONS.find((option) => option.value === routeMode)?.label ?? routeMode}
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

function initialMembers(
  group: AiRouteGroupItem | undefined,
  template: RouteTemplate | undefined,
): MemberDraft[] {
  if (group) {
    return memberDrafts(group).map((member) => ({
      model: member.model,
      weight: member.weight ?? 1,
      tierOverride: member.tierOverride ?? "AUTO",
      enabled: member.enabled !== false,
    }));
  }
  return (template?.models ?? []).map((model) => ({
    model: model.metadata.name,
    weight: 1,
    tierOverride: "AUTO",
    enabled: true,
  }));
}
