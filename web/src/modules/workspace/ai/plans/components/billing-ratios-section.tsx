import { type Resolver, useFieldArray, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  ProviderDialogSection,
  ProviderFieldRow,
} from "@/modules/workspace/ai/providers/components/provider-dialog-shell";
import {
  type AiModelTier,
  type AiSettingItem,
  type AiUsageRatios,
  fetchAiSetting,
  updateAiSetting,
} from "@/modules/workspace/ai/providers/lib/api";
import { MODEL_TIER_OPTIONS } from "@/modules/workspace/ai/providers/lib/model";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { UsageErrorState } from "../../usage/components/usage-state";
import { resolvePlanApiError } from "../lib/api";
import {
  RATIO_FIELDS,
  billingModelMultipliers,
  billingRatios,
  billingTierMultipliers,
  tierMultiplierLabel,
} from "../lib/model";

const DECIMAL_PATTERN = /^\d+(\.\d+)?$/;

const decimalField = z
  .string()
  .trim()
  .regex(DECIMAL_PATTERN, "请输入数字。")
  .refine((value) => Number(value) > 0, "必须大于 0。");

const billingSchema = z
  .object({
    ratios: z.object({
      input: decimalField,
      output: decimalField,
      cacheRead: decimalField,
      cacheWrite: decimalField,
      imagePerItem: decimalField,
      videoPerSecond: decimalField,
      audioPerSecond: decimalField,
      speechPerKiloChar: decimalField,
      toolCall: decimalField,
    }),
    tiers: z.object({
      CHEAP: decimalField,
      MEDIUM: decimalField,
      HIGH: decimalField,
      ULTRA: decimalField,
    }),
    models: z.array(
      z.object({
        code: z.string().trim().min(1, "请输入模型编码。").max(200, "模型编码过长。"),
        multiplier: decimalField,
      }),
    ),
  })
  .superRefine((values, context) => {
    const seen = new Set<string>();
    values.models.forEach((model, index) => {
      if (seen.has(model.code)) {
        context.addIssue({
          code: "custom",
          path: ["models", index, "code"],
          message: "模型编码不能重复。",
        });
      }
      seen.add(model.code);
    });
  });

type BillingFormValues = z.infer<typeof billingSchema>;

type BillingRatiosSectionProps = {
  canEdit: boolean;
};

export function BillingRatiosSection({ canEdit }: BillingRatiosSectionProps) {
  const settingQuery = useQuery({
    queryKey: ["ai-settings", "ai-setting"],
    queryFn: fetchAiSetting,
    retry: false,
  });

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="计费比例"
        description="配置标准权重、模型档位系数与模型覆盖系数；权重最终向上取整为标准 Token。"
      />
      {settingQuery.isError ? (
        <UsageErrorState
          title="无法加载计费设置"
          message={resolvePlanApiError(settingQuery.error)}
          onRetry={() => void settingQuery.refetch()}
        />
      ) : settingQuery.isPending ? (
        <ConsoleEmpty>正在加载计费设置…</ConsoleEmpty>
      ) : (
        <BillingRatiosForm setting={settingQuery.data} canEdit={canEdit} />
      )}
    </ConsoleSection>
  );
}

type BillingRatiosFormProps = {
  setting: AiSettingItem;
  canEdit: boolean;
};

function BillingRatiosForm({ setting, canEdit }: BillingRatiosFormProps) {
  const queryClient = useQueryClient();
  const billing = setting.spec.billing;
  const form = useForm<BillingFormValues>({
    resolver: zodResolver(billingSchema) as Resolver<BillingFormValues>,
    defaultValues: initialValues(billing),
  });
  const modelFields = useFieldArray({ control: form.control, name: "models" });
  const tiers = billingTierMultipliers(billing);

  const mutation = useMutation({
    mutationFn: (values: BillingFormValues) => {
      const ratios: AiUsageRatios = {};
      for (const field of RATIO_FIELDS) {
        ratios[field.key] = Number(values.ratios[field.key]);
      }
      const modelMultipliers: Record<string, number> = {};
      for (const model of values.models) {
        modelMultipliers[model.code.trim()] = Number(model.multiplier);
      }
      const tierMultipliers: Record<AiModelTier, number> = {
        CHEAP: Number(values.tiers.CHEAP),
        MEDIUM: Number(values.tiers.MEDIUM),
        HIGH: Number(values.tiers.HIGH),
        ULTRA: Number(values.tiers.ULTRA),
      };
      return updateAiSetting({
        ...setting,
        spec: {
          ...setting.spec,
          billing: {
            ...(setting.spec.billing ?? {}),
            ratios,
            tierMultipliers,
            modelMultipliers,
          },
        },
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-settings"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
      toast.success("计费比例已保存。");
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <form onSubmit={onSubmit} className="space-y-5 p-5">
      {!canEdit ? (
        <p className="text-xs text-muted-foreground">当前角色只能查看计费比例，无权修改。</p>
      ) : null}

      <ProviderDialogSection title="标准权重">
        <p className="text-xs text-muted-foreground">
          输入、缓存与输出等原始用量乘以对应比例后得到标准权重。
        </p>
        <div className="grid gap-3 sm:grid-cols-3">
          {RATIO_FIELDS.map((field) => (
            <ProviderFieldRow
              key={field.key}
              label={field.label}
              hint={field.hint}
              error={form.formState.errors.ratios?.[field.key]?.message}
            >
              <Input
                type="number"
                min={0}
                step={0.01}
                disabled={!canEdit}
                {...form.register(`ratios.${field.key}`)}
              />
            </ProviderFieldRow>
          ))}
        </div>
      </ProviderDialogSection>

      <ProviderDialogSection title="档位系数">
        <div className="grid gap-3 sm:grid-cols-4">
          {MODEL_TIER_OPTIONS.map((option) => (
            <ProviderFieldRow
              key={option.value}
              label={tierMultiplierLabel(option.value)}
              hint={`默认 ${tiers[option.value]}`}
              error={form.formState.errors.tiers?.[option.value]?.message}
            >
              <Input
                type="number"
                min={0}
                step={0.1}
                disabled={!canEdit}
                {...form.register(`tiers.${option.value}`)}
              />
            </ProviderFieldRow>
          ))}
        </div>
      </ProviderDialogSection>

      <ProviderDialogSection title="模型覆盖系数">
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground">
            键为模型编码（modelCode），未配置时使用档位系数；模型系数与档位系数相乘。
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={!canEdit || modelFields.fields.length >= 100}
            onClick={() => modelFields.append({ code: "", multiplier: "1" })}
          >
            <Plus />
            添加模型
          </Button>
        </div>
        {modelFields.fields.length === 0 ? (
          <p className="text-xs text-muted-foreground">暂无模型覆盖系数。</p>
        ) : (
          <div className="space-y-2">
            {modelFields.fields.map((field, index) => (
              <div key={field.id} className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_120px_auto]">
                <div className="space-y-1">
                  <Input
                    placeholder="模型编码"
                    disabled={!canEdit}
                    {...form.register(`models.${index}.code`)}
                  />
                  {form.formState.errors.models?.[index]?.code?.message ? (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.models[index]?.code?.message}
                    </p>
                  ) : null}
                </div>
                <div className="space-y-1">
                  <Input
                    type="number"
                    min={0}
                    step={0.1}
                    disabled={!canEdit}
                    {...form.register(`models.${index}.multiplier`)}
                  />
                  {form.formState.errors.models?.[index]?.multiplier?.message ? (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.models[index]?.multiplier?.message}
                    </p>
                  ) : null}
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label="移除模型系数"
                  disabled={!canEdit}
                  onClick={() => modelFields.remove(index)}
                >
                  <Trash2 />
                </Button>
              </div>
            ))}
          </div>
        )}
      </ProviderDialogSection>

      {canEdit ? (
        <div className="flex justify-end">
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
            保存计费比例
          </Button>
        </div>
      ) : null}
    </form>
  );
}

function initialValues(billing: AiSettingItem["spec"]["billing"]): BillingFormValues {
  const ratios = billingRatios(billing);
  const tiers = billingTierMultipliers(billing);
  return {
    ratios: {
      input: String(ratios.input),
      output: String(ratios.output),
      cacheRead: String(ratios.cacheRead),
      cacheWrite: String(ratios.cacheWrite),
      imagePerItem: String(ratios.imagePerItem),
      videoPerSecond: String(ratios.videoPerSecond),
      audioPerSecond: String(ratios.audioPerSecond),
      speechPerKiloChar: String(ratios.speechPerKiloChar),
      toolCall: String(ratios.toolCall),
    },
    tiers: {
      CHEAP: String(tiers.CHEAP),
      MEDIUM: String(tiers.MEDIUM),
      HIGH: String(tiers.HIGH),
      ULTRA: String(tiers.ULTRA),
    },
    models: billingModelMultipliers(billing).map(([code, multiplier]) => ({
      code,
      multiplier: String(multiplier),
    })),
  };
}
