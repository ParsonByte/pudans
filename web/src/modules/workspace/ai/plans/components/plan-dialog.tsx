import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ReceiptText } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Checkbox } from "@/components/ui/checkbox";
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
import {
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "@/modules/workspace/ai/providers/lib/model";

import {
  type AiPlanItem,
  type AiPlanSpec,
  type PlanDayOfWeek,
  type PlanFallbackAction,
  type PlanFeature,
  type PlanVoucherPeriod,
  buildPlanItem,
  createPlan,
  resolvePlanApiError,
  updatePlan,
} from "../lib/api";
import {
  PLAN_DAY_OF_WEEK_OPTIONS,
  PLAN_FALLBACK_OPTIONS,
  PLAN_FEATURE_OPTIONS,
  PLAN_VOUCHER_PERIOD_OPTIONS,
  joinPlanList,
  splitPlanList,
} from "../lib/model";

const DIGITS_PATTERN = /^\d+$/;

function digitField(message: string) {
  return z
    .string()
    .trim()
    .regex(DIGITS_PATTERN, message)
    .refine((value) => Number.isSafeInteger(Number(value)), message);
}

const planSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "请输入名称。")
      .max(63, "名称不能超过 63 个字符。")
      .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
    displayName: z.string().trim().min(1, "请输入显示名称。").max(100, "显示名称过长。"),
    description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
    enabled: z.boolean(),
    fiveHourLimit: digitField("请输入 5 小时上限。").refine(
      (value) => Number(value) > 0,
      "5 小时上限必须大于 0。",
    ),
    weeklyLimit: digitField("请输入周上限。").refine(
      (value) => Number(value) > 0,
      "周上限必须大于 0。",
    ),
    resetDayOfWeek: z.enum([
      "MONDAY",
      "TUESDAY",
      "WEDNESDAY",
      "THURSDAY",
      "FRIDAY",
      "SATURDAY",
      "SUNDAY",
    ]),
    resetHour: digitField("请输入 0 到 23 的重置小时。").refine(
      (value) => Number(value) <= 23,
      "重置小时不能超过 23。",
    ),
    toleranceUnits: digitField("请输入容差。"),
    voucherFiveHour: digitField("请输入 5 小时重置券张数。"),
    voucherWeekly: digitField("请输入周重置券张数。"),
    voucherPeriod: z.enum(["WEEKLY", "MONTHLY"]),
    maxConcurrency: digitField("请输入并发上限。"),
    maxRpm: digitField("请输入每分钟请求上限。"),
    fallbackAction: z.enum(["DENY", "DOWNGRADE"]),
    downgradeRouteGroup: z.string().trim().max(63, "降级路由组名称过长。"),
    routeGroupsText: z.string().trim().max(500, "允许的路由组列表过长。"),
    modelsText: z.string().trim().max(2000, "允许的模型列表过长。"),
    features: z.array(z.enum(["CHAT", "KNOWLEDGE", "AGENT", "SUBAGENT"])),
  })
  .superRefine((values, context) => {
    if (
      DIGITS_PATTERN.test(values.weeklyLimit) &&
      DIGITS_PATTERN.test(values.fiveHourLimit) &&
      Number(values.weeklyLimit) < Number(values.fiveHourLimit)
    ) {
      context.addIssue({
        code: "custom",
        path: ["weeklyLimit"],
        message: "周上限不能小于 5 小时上限。",
      });
    }
    if (values.fallbackAction === "DOWNGRADE" && !values.downgradeRouteGroup) {
      context.addIssue({
        code: "custom",
        path: ["downgradeRouteGroup"],
        message: "降级策略必须填写降级路由组。",
      });
    }
  });

type PlanFormValues = z.infer<typeof planSchema>;

type PlanDialogProps = {
  plan?: AiPlanItem;
  onClose: () => void;
};

export function PlanDialog({ plan, onClose }: PlanDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<PlanFormValues>({
    resolver: zodResolver(planSchema) as Resolver<PlanFormValues>,
    defaultValues: initialValues(plan),
  });
  const enabled = useWatch({ control: form.control, name: "enabled" });
  const resetDayOfWeek = useWatch({ control: form.control, name: "resetDayOfWeek" });
  const voucherPeriod = useWatch({ control: form.control, name: "voucherPeriod" });
  const fallbackAction = useWatch({ control: form.control, name: "fallbackAction" });
  const features = useWatch({ control: form.control, name: "features" });

  const toggleFeature = (feature: PlanFeature, checked: boolean) => {
    const next = checked ? [...features, feature] : features.filter((item) => item !== feature);
    form.setValue("features", next, { shouldDirty: true });
  };

  const mutation = useMutation({
    mutationFn: (values: PlanFormValues) => {
      const current = plan?.spec;
      const spec: AiPlanSpec = {
        ...(current ?? {}),
        displayName: values.displayName,
        description: values.description || undefined,
        enabled: values.enabled,
        quota: {
          ...(current?.quota ?? {}),
          fiveHour: {
            ...(current?.quota?.fiveHour ?? {}),
            limit: Number(values.fiveHourLimit),
          },
          weekly: {
            ...(current?.quota?.weekly ?? {}),
            limit: Number(values.weeklyLimit),
            resetDayOfWeek: values.resetDayOfWeek,
            resetHour: Number(values.resetHour),
          },
        },
        toleranceUnits: Number(values.toleranceUnits),
        vouchers: {
          ...(current?.vouchers ?? {}),
          fiveHour: Number(values.voucherFiveHour),
          weekly: Number(values.voucherWeekly),
          period: values.voucherPeriod,
        },
        rateLimit: {
          maxConcurrency: Number(values.maxConcurrency),
          maxRpm: Number(values.maxRpm),
        },
        fallback: {
          onFiveHourExhausted: values.fallbackAction,
          downgradeRouteGroup:
            values.fallbackAction === "DOWNGRADE" ? values.downgradeRouteGroup : undefined,
        },
        access: {
          routeGroups: splitPlanList(values.routeGroupsText),
          models: splitPlanList(values.modelsText),
          features: values.features,
        },
      };
      if (plan) {
        return updatePlan(buildPlanItem(plan.metadata.name, spec, plan));
      }
      return createPlan(buildPlanItem(values.name, spec));
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-plans"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
      toast.success(plan ? "套餐已保存。" : "套餐已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<ReceiptText />}
      title={plan ? "编辑套餐" : "新建套餐"}
      description="套餐按北京时间计算 5 小时滚动窗口与每周固定重置窗口。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      submitLabel={plan ? "保存套餐" : "创建套餐"}
      contentClassName="sm:max-w-2xl"
      maxHeightRatio={0.6}
    >
      <FormDialogSection title="基础信息">
        <div className="space-y-3">
          <FormField
            label="名称"
            htmlFor="ai-plan-name"
            hint={plan ? "名称创建后不可修改。" : RESOURCE_NAME_HINT}
            error={form.formState.errors.name?.message}
          >
            <Input id="ai-plan-name" disabled={Boolean(plan)} {...form.register("name")} />
          </FormField>
          <FormField
            label="显示名称"
            htmlFor="ai-plan-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input id="ai-plan-display-name" {...form.register("displayName")} />
          </FormField>
          <FormField
            label="描述"
            htmlFor="ai-plan-description"
            error={form.formState.errors.description?.message}
          >
            <Textarea id="ai-plan-description" rows={2} {...form.register("description")} />
          </FormField>
          <FormField label="启用" htmlFor="ai-plan-enabled" hint="禁用后新请求不再解析到该套餐。">
            <Switch
              id="ai-plan-enabled"
              checked={enabled}
              onCheckedChange={(checked) =>
                form.setValue("enabled", checked, { shouldDirty: true })
              }
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="额度窗口">
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField
              label="5 小时上限"
              htmlFor="ai-plan-five-hour-limit"
              hint="标准 Token，滚动 5 小时窗口。"
              error={form.formState.errors.fiveHourLimit?.message}
            >
              <Input
                id="ai-plan-five-hour-limit"
                type="number"
                min={1}
                step={1}
                {...form.register("fiveHourLimit")}
              />
            </FormField>
            <FormField
              label="周上限"
              htmlFor="ai-plan-weekly-limit"
              hint="必须不小于 5 小时上限。"
              error={form.formState.errors.weeklyLimit?.message}
            >
              <Input
                id="ai-plan-weekly-limit"
                type="number"
                min={1}
                step={1}
                {...form.register("weeklyLimit")}
              />
            </FormField>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField
              label="重置星期"
              htmlFor="ai-plan-reset-day"
              hint="周窗口按北京时间固定重置。"
              error={form.formState.errors.resetDayOfWeek?.message}
            >
              <Select
                value={resetDayOfWeek}
                onValueChange={(value) =>
                  form.setValue("resetDayOfWeek", value as PlanDayOfWeek, { shouldDirty: true })
                }
              >
                <SelectTrigger id="ai-plan-reset-day" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PLAN_DAY_OF_WEEK_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
            <FormField
              label="重置小时"
              htmlFor="ai-plan-reset-hour"
              hint="北京时间 0 到 23 点。"
              error={form.formState.errors.resetHour?.message}
            >
              <Input
                id="ai-plan-reset-hour"
                type="number"
                min={0}
                max={23}
                step={1}
                {...form.register("resetHour")}
              />
            </FormField>
          </div>
          <FormField
            label="结算容差"
            htmlFor="ai-plan-tolerance"
            hint="实际用量超出预留时的容忍标准 Token 数。"
            error={form.formState.errors.toleranceUnits?.message}
          >
            <Input
              id="ai-plan-tolerance"
              type="number"
              min={0}
              step={1}
              {...form.register("toleranceUnits")}
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="限流与降级">
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField
              label="并发上限"
              htmlFor="ai-plan-max-concurrency"
              hint="0 表示不限制。"
              error={form.formState.errors.maxConcurrency?.message}
            >
              <Input
                id="ai-plan-max-concurrency"
                type="number"
                min={0}
                step={1}
                {...form.register("maxConcurrency")}
              />
            </FormField>
            <FormField
              label="每分钟请求上限"
              htmlFor="ai-plan-max-rpm"
              hint="0 表示不限制。"
              error={form.formState.errors.maxRpm?.message}
            >
              <Input
                id="ai-plan-max-rpm"
                type="number"
                min={0}
                step={1}
                {...form.register("maxRpm")}
              />
            </FormField>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField
              label="5 小时用尽策略"
              htmlFor="ai-plan-fallback-action"
              error={form.formState.errors.fallbackAction?.message}
            >
              <Select
                value={fallbackAction}
                onValueChange={(value) =>
                  form.setValue("fallbackAction", value as PlanFallbackAction, {
                    shouldDirty: true,
                  })
                }
              >
                <SelectTrigger id="ai-plan-fallback-action" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PLAN_FALLBACK_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
            <FormField
              label="降级路由组"
              htmlFor="ai-plan-downgrade-route-group"
              hint="选择降级时必填。"
              error={form.formState.errors.downgradeRouteGroup?.message}
            >
              <Input
                id="ai-plan-downgrade-route-group"
                disabled={fallbackAction !== "DOWNGRADE"}
                {...form.register("downgradeRouteGroup")}
              />
            </FormField>
          </div>
        </div>
      </FormDialogSection>

      <FormDialogSection title="访问范围">
        <div className="space-y-3">
          <FormField
            label="允许的路由组"
            htmlFor="ai-plan-access-route-groups"
            hint="逗号分隔；留空表示不限制。"
            error={form.formState.errors.routeGroupsText?.message}
          >
            <Input
              id="ai-plan-access-route-groups"
              placeholder="default, economy"
              {...form.register("routeGroupsText")}
            />
          </FormField>
          <FormField
            label="允许的模型"
            htmlFor="ai-plan-access-models"
            hint="填模型资源名称，逗号分隔；留空表示不限制。"
            error={form.formState.errors.modelsText?.message}
          >
            <Input
              id="ai-plan-access-models"
              placeholder="model-standard, model-pro"
              {...form.register("modelsText")}
            />
          </FormField>
          <FormField label="允许的功能" htmlFor="ai-plan-feature-chat" hint="不选择表示不限制。">
            <div className="flex flex-wrap gap-4">
              {PLAN_FEATURE_OPTIONS.map((option) => (
                <label key={option.value} className="flex items-center gap-2 text-sm">
                  <Checkbox
                    id={`ai-plan-feature-${option.value.toLowerCase()}`}
                    checked={features.includes(option.value)}
                    onCheckedChange={(checked) => toggleFeature(option.value, checked === true)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="重置券">
        <div className="grid gap-3 sm:grid-cols-3">
          <FormField
            label="5 小时券"
            htmlFor="ai-plan-voucher-five-hour"
            hint="每周期张数。"
            error={form.formState.errors.voucherFiveHour?.message}
          >
            <Input
              id="ai-plan-voucher-five-hour"
              type="number"
              min={0}
              step={1}
              {...form.register("voucherFiveHour")}
            />
          </FormField>
          <FormField
            label="周券"
            htmlFor="ai-plan-voucher-weekly"
            hint="每周期张数。"
            error={form.formState.errors.voucherWeekly?.message}
          >
            <Input
              id="ai-plan-voucher-weekly"
              type="number"
              min={0}
              step={1}
              {...form.register("voucherWeekly")}
            />
          </FormField>
          <FormField
            label="发放周期"
            htmlFor="ai-plan-voucher-period"
            error={form.formState.errors.voucherPeriod?.message}
          >
            <Select
              value={voucherPeriod}
              onValueChange={(value) =>
                form.setValue("voucherPeriod", value as PlanVoucherPeriod, { shouldDirty: true })
              }
            >
              <SelectTrigger id="ai-plan-voucher-period" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PLAN_VOUCHER_PERIOD_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
        </div>
        <p className="text-xs text-muted-foreground">
          重置券按周期补充，未使用不结转；管理员可在「重置券发放」中追加。
        </p>
      </FormDialogSection>
    </FormDialog>
  );
}

function initialValues(plan: AiPlanItem | undefined): PlanFormValues {
  const spec = plan?.spec;
  return {
    name: plan?.metadata.name ?? "",
    displayName: spec?.displayName ?? "",
    description: spec?.description ?? "",
    enabled: spec?.enabled ?? true,
    fiveHourLimit: spec?.quota?.fiveHour?.limit == null ? "" : String(spec.quota.fiveHour.limit),
    weeklyLimit: spec?.quota?.weekly?.limit == null ? "" : String(spec.quota.weekly.limit),
    resetDayOfWeek: spec?.quota?.weekly?.resetDayOfWeek ?? "MONDAY",
    resetHour: spec?.quota?.weekly?.resetHour == null ? "0" : String(spec.quota.weekly.resetHour),
    toleranceUnits: spec?.toleranceUnits == null ? "5000" : String(spec.toleranceUnits),
    voucherFiveHour: spec?.vouchers?.fiveHour == null ? "0" : String(spec.vouchers.fiveHour),
    voucherWeekly: spec?.vouchers?.weekly == null ? "0" : String(spec.vouchers.weekly),
    voucherPeriod: spec?.vouchers?.period ?? "MONTHLY",
    maxConcurrency:
      spec?.rateLimit?.maxConcurrency == null ? "0" : String(spec.rateLimit.maxConcurrency),
    maxRpm: spec?.rateLimit?.maxRpm == null ? "0" : String(spec.rateLimit.maxRpm),
    fallbackAction: spec?.fallback?.onFiveHourExhausted ?? "DENY",
    downgradeRouteGroup: spec?.fallback?.downgradeRouteGroup ?? "",
    routeGroupsText: joinPlanList(spec?.access?.routeGroups),
    modelsText: joinPlanList(spec?.access?.models),
    features: spec?.access?.features ?? [],
  };
}
