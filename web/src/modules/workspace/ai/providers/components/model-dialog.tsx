import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Boxes } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";

import {
  AI_API_VERSION,
  AI_MODEL_KIND,
  type AiModelItem,
  type AiModelTier,
  type AiModelType,
  type AiProviderItem,
  createModel,
  resolveAiProviderApiError,
  updateModel,
} from "../lib/api";
import {
  MODEL_TIER_OPTIONS,
  MODEL_TYPE_OPTIONS,
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "../lib/model";
import {
  ProviderDialogSection,
  ProviderDialogShell,
  ProviderFieldRow,
} from "./provider-dialog-shell";

const MODEL_TYPE_VALUES = MODEL_TYPE_OPTIONS.map((option) => option.value) as [
  AiModelType,
  ...AiModelType[],
];

const MODEL_TIER_VALUES = ["NONE", ...MODEL_TIER_OPTIONS.map((option) => option.value)] as [
  string,
  ...string[],
];

const modelSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入名称。")
    .max(63, "名称不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  modelCode: z.string().trim().min(1, "请输入模型编码。").max(200, "模型编码过长。"),
  displayName: z.string().trim().max(100, "显示名称不能超过 100 个字符。"),
  modelType: z.enum(MODEL_TYPE_VALUES),
  tier: z.enum(MODEL_TIER_VALUES),
  contextWindow: z.coerce.number().int().min(0, "上下文长度不能为负数。").max(10000000),
  maxOutputTokens: z.coerce.number().int().min(0, "输出长度不能为负数。").max(10000000),
  embeddingDim: z.coerce.number().int().min(0, "向量维度不能为负数。").max(100000),
  supportsStream: z.boolean(),
  supportsTools: z.boolean(),
  supportsJson: z.boolean(),
  supportsVision: z.boolean(),
  supportsAsync: z.boolean(),
  enabled: z.boolean(),
});

type ModelFormValues = z.infer<typeof modelSchema>;

type ModelDialogProps = {
  provider: AiProviderItem;
  model?: AiModelItem;
  onClose: () => void;
};

export function ModelDialog({ provider, model, onClose }: ModelDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<ModelFormValues>({
    resolver: zodResolver(modelSchema) as Resolver<ModelFormValues>,
    defaultValues: initialValues(model),
  });
  const modelType = useWatch({ control: form.control, name: "modelType" });
  const tier = useWatch({ control: form.control, name: "tier" });
  const supportsStream = useWatch({ control: form.control, name: "supportsStream" });
  const supportsTools = useWatch({ control: form.control, name: "supportsTools" });
  const supportsJson = useWatch({ control: form.control, name: "supportsJson" });
  const supportsVision = useWatch({ control: form.control, name: "supportsVision" });
  const supportsAsync = useWatch({ control: form.control, name: "supportsAsync" });
  const enabled = useWatch({ control: form.control, name: "enabled" });

  const mutation = useMutation({
    mutationFn: (values: ModelFormValues) => {
      const spec = {
        ...(model?.spec ?? {}),
        provider: provider.metadata.name,
        modelCode: values.modelCode,
        displayName: values.displayName || undefined,
        modelType: values.modelType,
        tier: values.tier === "NONE" ? undefined : (values.tier as AiModelTier),
        contextWindow: values.contextWindow > 0 ? values.contextWindow : undefined,
        maxOutputTokens: values.maxOutputTokens > 0 ? values.maxOutputTokens : undefined,
        embeddingDim:
          values.modelType === "EMBEDDING" && values.embeddingDim > 0
            ? values.embeddingDim
            : undefined,
        supportsStream: values.supportsStream,
        supportsTools: values.supportsTools,
        supportsJson: values.supportsJson,
        supportsVision: values.supportsVision,
        supportsAsync: values.supportsAsync,
        enabled: values.enabled,
      };
      if (model) {
        return updateModel({ ...model, spec });
      }
      return createModel({
        apiVersion: AI_API_VERSION,
        kind: AI_MODEL_KIND,
        metadata: { name: values.name },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-models"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-providers"] });
      toast.success(model ? "模型已保存。" : "模型已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <ProviderDialogShell
      onClose={onClose}
      icon={<Boxes />}
      title={model ? "编辑模型" : "添加模型"}
      description={`为供应商 ${provider.spec.displayName || provider.metadata.name} 配置可用模型。`}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      submitLabel={model ? "保存模型" : "创建模型"}
    >
      <ProviderDialogSection title="基础信息">
        <div className="space-y-3">
          <ProviderFieldRow
            label="名称"
            htmlFor="ai-model-name"
            hint={model ? "名称创建后不可修改。" : RESOURCE_NAME_HINT}
            error={form.formState.errors.name?.message}
          >
            <Input id="ai-model-name" disabled={Boolean(model)} {...form.register("name")} />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="模型编码"
            htmlFor="ai-model-code"
            hint="供应商接口使用的模型标识。"
            error={form.formState.errors.modelCode?.message}
          >
            <Input id="ai-model-code" placeholder="gpt-4o-mini" {...form.register("modelCode")} />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="显示名称"
            htmlFor="ai-model-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input id="ai-model-display-name" {...form.register("displayName")} />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="类型"
            htmlFor="ai-model-type"
            error={form.formState.errors.modelType?.message}
          >
            <Select
              value={modelType}
              onValueChange={(value) =>
                form.setValue("modelType", value as AiModelType, { shouldDirty: true })
              }
            >
              <SelectTrigger id="ai-model-type" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {MODEL_TYPE_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </ProviderFieldRow>
          <ProviderFieldRow
            label="档位"
            htmlFor="ai-model-tier"
            error={form.formState.errors.tier?.message}
          >
            <Select
              value={tier}
              onValueChange={(value) => form.setValue("tier", value, { shouldDirty: true })}
            >
              <SelectTrigger id="ai-model-tier" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NONE">未设置</SelectItem>
                {MODEL_TIER_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </ProviderFieldRow>
          <ProviderFieldRow
            label="上下文长度"
            htmlFor="ai-model-context-window"
            hint="0 表示未设置。"
            error={form.formState.errors.contextWindow?.message}
          >
            <Input
              id="ai-model-context-window"
              type="number"
              min={0}
              {...form.register("contextWindow")}
            />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="最大输出"
            htmlFor="ai-model-max-output"
            hint="0 表示未设置。"
            error={form.formState.errors.maxOutputTokens?.message}
          >
            <Input
              id="ai-model-max-output"
              type="number"
              min={0}
              {...form.register("maxOutputTokens")}
            />
          </ProviderFieldRow>
          {modelType === "EMBEDDING" ? (
            <ProviderFieldRow
              label="向量维度"
              htmlFor="ai-model-embedding-dim"
              hint="0 表示未设置。"
              error={form.formState.errors.embeddingDim?.message}
            >
              <Input
                id="ai-model-embedding-dim"
                type="number"
                min={0}
                {...form.register("embeddingDim")}
              />
            </ProviderFieldRow>
          ) : null}
        </div>
      </ProviderDialogSection>

      <ProviderDialogSection title="能力">
        <div className="grid gap-3 sm:grid-cols-2">
          <SwitchRow
            label="流式输出"
            checked={supportsStream}
            onChange={(checked) => form.setValue("supportsStream", checked, { shouldDirty: true })}
          />
          <SwitchRow
            label="工具调用"
            checked={supportsTools}
            onChange={(checked) => form.setValue("supportsTools", checked, { shouldDirty: true })}
          />
          <SwitchRow
            label="JSON 输出"
            checked={supportsJson}
            onChange={(checked) => form.setValue("supportsJson", checked, { shouldDirty: true })}
          />
          <SwitchRow
            label="图像理解"
            checked={supportsVision}
            onChange={(checked) => form.setValue("supportsVision", checked, { shouldDirty: true })}
          />
          <SwitchRow
            label="异步任务"
            checked={supportsAsync}
            onChange={(checked) => form.setValue("supportsAsync", checked, { shouldDirty: true })}
          />
          <SwitchRow
            label="启用模型"
            checked={enabled}
            onChange={(checked) => form.setValue("enabled", checked, { shouldDirty: true })}
          />
        </div>
      </ProviderDialogSection>
    </ProviderDialogShell>
  );
}

type SwitchRowProps = {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
};

function SwitchRow({ label, checked, onChange }: SwitchRowProps) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border p-3">
      <p className="text-sm font-medium">{label}</p>
      <Switch checked={checked} onCheckedChange={onChange} aria-label={label} />
    </div>
  );
}

function initialValues(model: AiModelItem | undefined): ModelFormValues {
  const spec = model?.spec;
  return {
    name: model?.metadata.name ?? "",
    modelCode: spec?.modelCode ?? "",
    displayName: spec?.displayName ?? "",
    modelType: spec?.modelType ?? "CHAT",
    tier: spec?.tier ?? "NONE",
    contextWindow: spec?.contextWindow ?? 0,
    maxOutputTokens: spec?.maxOutputTokens ?? 0,
    embeddingDim: spec?.embeddingDim ?? 0,
    supportsStream: spec?.supportsStream ?? true,
    supportsTools: spec?.supportsTools ?? false,
    supportsJson: spec?.supportsJson ?? false,
    supportsVision: spec?.supportsVision ?? false,
    supportsAsync: spec?.supportsAsync ?? false,
    enabled: spec?.enabled ?? true,
  };
}
