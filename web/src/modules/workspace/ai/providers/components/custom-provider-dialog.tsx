import { useState } from "react";
import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Boxes, ExternalLink } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";

import {
  AI_API_VERSION,
  AI_MODEL_KIND,
  AI_PROVIDER_ACCOUNT_KIND,
  AI_PROVIDER_KIND,
  type AiProviderItem,
  createModel,
  createProvider,
  createProviderAccount,
  resolveAiProviderApiError,
} from "../lib/api";
import {
  DEFAULT_PROVIDER_SPEC,
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
  modelNameFromCode,
  vendorEndpoint,
} from "../lib/model";
import {
  type ProviderPreset,
  type ProviderRegionId,
  presetRegion,
  vendorLabel,
} from "../lib/presets";
import { ProviderBrandIcon } from "./provider-brand-icon";
import { ProviderDialogShell, ProviderFieldRow } from "./provider-dialog-shell";

const customProviderSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入 ID。")
    .max(63, "ID 不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  region: z.string(),
  baseUrl: z
    .string()
    .trim()
    .min(1, "请输入 Base URL。")
    .max(500, "地址不能超过 500 个字符。")
    .refine((value) => /^https?:\/\//i.test(value), "请输入以 http:// 或 https:// 开头的地址。"),
  apiKey: z.string().trim().max(1000, "密钥过长。"),
  modelCodes: z.string().trim().max(4000, "模型列表过长。"),
  enabled: z.boolean(),
});

type CustomProviderFormValues = z.infer<typeof customProviderSchema>;

type CustomProviderDialogProps = {
  preset?: ProviderPreset;
  onCreated?: (provider: AiProviderItem) => void;
  onClose: () => void;
};

export function CustomProviderDialog({ preset, onCreated, onClose }: CustomProviderDialogProps) {
  const queryClient = useQueryClient();
  const [showKey, setShowKey] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const firstRegion = preset ? preset.regions[0] : undefined;
  const selectedVendor = preset?.vendor ?? "CUSTOM";
  const form = useForm<CustomProviderFormValues>({
    resolver: zodResolver(customProviderSchema) as Resolver<CustomProviderFormValues>,
    defaultValues: {
      name: preset?.id ?? "",
      displayName: preset?.displayName ?? "",
      region: firstRegion?.id ?? "global",
      baseUrl: preset && firstRegion ? firstRegion.baseUrl : "",
      apiKey: "",
      modelCodes: "",
      enabled: true,
    },
  });
  const regionId = useWatch({ control: form.control, name: "region" });
  const enabled = useWatch({ control: form.control, name: "enabled" });
  const baseUrl = useWatch({ control: form.control, name: "baseUrl" });
  const activeRegion = preset ? presetRegion(preset, regionId as ProviderRegionId) : undefined;
  const keyUrl = preset ? activeRegion?.keysUrl || preset.keysUrl : undefined;
  const endpoint = vendorEndpoint(selectedVendor, baseUrl);

  const selectRegion = (nextRegion: ProviderRegionId) => {
    form.setValue("region", nextRegion, { shouldDirty: true });
    if (!preset) {
      return;
    }
    const currentName = form.getValues("name");
    const currentDisplayName = form.getValues("displayName");
    const regionSuffix = nextRegion === "cn" ? "-cn" : "";
    if (currentName === preset.id || currentName === `${preset.id}-cn`) {
      form.setValue("name", `${preset.id}${regionSuffix}`, { shouldDirty: true });
    }
    if (
      currentDisplayName === preset.displayName ||
      currentDisplayName === `${preset.displayName} (China)`
    ) {
      form.setValue(
        "displayName",
        nextRegion === "cn" ? `${preset.displayName} (China)` : preset.displayName,
        { shouldDirty: true },
      );
    }
    form.setValue("baseUrl", presetRegion(preset, nextRegion).baseUrl, {
      shouldDirty: true,
    });
  };

  const mutation = useMutation({
    mutationFn: async (values: CustomProviderFormValues) => {
      const provider = await createProvider({
        apiVersion: AI_API_VERSION,
        kind: AI_PROVIDER_KIND,
        metadata: { name: values.name },
        spec: {
          displayName: values.displayName,
          vendor: selectedVendor,
          baseUrl: values.baseUrl,
          routing: DEFAULT_PROVIDER_SPEC.routing,
          affinity: DEFAULT_PROVIDER_SPEC.affinity,
          maxConcurrency: DEFAULT_PROVIDER_SPEC.maxConcurrency,
          maxRPM: DEFAULT_PROVIDER_SPEC.maxRPM,
          connectTimeoutMs: DEFAULT_PROVIDER_SPEC.connectTimeoutMs,
          readTimeoutMs: DEFAULT_PROVIDER_SPEC.readTimeoutMs,
          free: false,
          enabled: values.enabled,
        },
      });

      const failures: string[] = [];
      let accountCreated = false;
      if (values.apiKey) {
        try {
          await createProviderAccount({
            apiVersion: AI_API_VERSION,
            kind: AI_PROVIDER_ACCOUNT_KIND,
            metadata: { name: `${values.name.slice(0, 50)}-main` },
            spec: {
              provider: values.name,
              displayName: `${values.displayName} 主密钥`,
              accountType: "API_KEY",
              apiKey: values.apiKey,
              weight: 1,
              priority: 0,
              enabled: true,
              freePlan: false,
              creditsEnabled: false,
            },
          });
          accountCreated = true;
        } catch (error) {
          failures.push(`密钥：${resolveAiProviderApiError(error)}`);
        }
      }

      const codes = parseModelCodes(values.modelCodes);
      const usedNames = new Set<string>();
      let modelCount = 0;
      for (const code of codes) {
        const modelName = modelNameFromCode(code) || `model-${modelCount + 1}`;
        if (usedNames.has(modelName)) {
          continue;
        }
        usedNames.add(modelName);
        try {
          await createModel({
            apiVersion: AI_API_VERSION,
            kind: AI_MODEL_KIND,
            metadata: { name: modelName },
            spec: {
              provider: values.name,
              modelCode: code,
              displayName: code,
              modelType: "CHAT",
              supportsStream: true,
              enabled: true,
            },
          });
          modelCount += 1;
        } catch (error) {
          failures.push(`模型 ${code}：${resolveAiProviderApiError(error)}`);
        }
      }

      return { provider, accountCreated, modelCount, failures };
    },
    onSuccess: async ({ provider, accountCreated, modelCount, failures }) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-providers"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-accounts"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-models"] });
      const details = [
        accountCreated ? "已添加主密钥" : null,
        modelCount > 0 ? `已创建 ${modelCount} 个模型` : null,
      ].filter(Boolean);
      toast.success(
        details.length > 0 ? `供应商已创建，${details.join("，")}。` : "供应商已创建。",
      );
      if (failures.length > 0) {
        toast.error(`部分配置创建失败：${failures[0]}`);
      }
      onCreated?.(provider);
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
      title={preset ? `添加 ${preset.displayName}` : "自定义供应商"}
      description={
        preset
          ? "选择地区，填入 API 密钥；保存后会打开编辑弹窗并自动拉取模型。"
          : "填写供应商 ID、地址和 API 密钥；平台会自动选择接入协议，保存后自动拉取模型。"
      }
      icon={
        preset ? (
          <ProviderBrandIcon preset={preset} className="size-5" />
        ) : (
          <Boxes className="size-4" />
        )
      }
      onSubmit={onSubmit}
      onClose={onClose}
      isPending={mutation.isPending}
      submitLabel="添加"
    >
      <ProviderFieldRow label="名称" error={form.formState.errors.displayName?.message}>
        <Input placeholder="例如 My Relay" {...form.register("displayName")} />
      </ProviderFieldRow>
      <ProviderFieldRow
        label="ID"
        hint={RESOURCE_NAME_HINT}
        error={form.formState.errors.name?.message}
      >
        <Input {...form.register("name")} />
      </ProviderFieldRow>

      <ProviderFieldRow label="供应商">
        <Badge variant="outline">{vendorLabel(selectedVendor)}</Badge>
      </ProviderFieldRow>

      {preset && preset.regions.length > 1 ? (
        <ProviderFieldRow label="区域 / 套餐">
          <Segmented
            options={preset.regions.map((region) => ({
              value: region.id,
              label: region.label,
            }))}
            value={regionId}
            onChange={(value) => selectRegion(value as ProviderRegionId)}
          />
        </ProviderFieldRow>
      ) : null}

      <ProviderFieldRow label="Base URL" error={form.formState.errors.baseUrl?.message}>
        <Input placeholder="https://api.example.com" {...form.register("baseUrl")} />
        {endpoint ? (
          <p className="truncate text-xs text-muted-foreground">示例请求：{endpoint}</p>
        ) : null}
      </ProviderFieldRow>

      <ProviderFieldRow
        label="API 密钥"
        hint={preset?.kind === "local" ? "本地服务通常无需密钥，可留空。" : undefined}
        error={form.formState.errors.apiKey?.message}
      >
        <div className="flex items-center gap-2">
          <Input
            type={showKey ? "text" : "password"}
            autoComplete="new-password"
            placeholder={preset?.kind === "local" ? "可留空" : "粘贴 API 密钥"}
            {...form.register("apiKey")}
          />
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setShowKey((open) => !open)}
          >
            {showKey ? "隐藏" : "显示"}
          </Button>
          {keyUrl && preset?.kind !== "local" ? (
            <Button type="button" variant="ghost" size="sm" asChild>
              <a href={keyUrl} target="_blank" rel="noreferrer">
                获取密钥
                <ExternalLink />
              </a>
            </Button>
          ) : null}
        </div>
      </ProviderFieldRow>

      {preset?.note ? <p className="text-xs text-muted-foreground">{preset.note}</p> : null}

      <ProviderFieldRow
        label="模型"
        hint="可选；多个模型用逗号或换行分隔，保存后创建为对话模型。"
        error={form.formState.errors.modelCodes?.message}
      >
        <Input
          placeholder="例如 deepseek-chat, deepseek-reasoner"
          {...form.register("modelCodes")}
        />
      </ProviderFieldRow>

      <button
        type="button"
        className="flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground"
        onClick={() => setAdvancedOpen((open) => !open)}
      >
        {advancedOpen ? "收起高级" : "高级"}
      </button>
      {advancedOpen ? (
        <div className="space-y-3 border p-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium">启用供应商</p>
              <p className="text-xs text-muted-foreground">关闭后不参与路由。</p>
            </div>
            <Switch
              checked={enabled}
              onCheckedChange={(checked) =>
                form.setValue("enabled", checked, { shouldDirty: true })
              }
              aria-label="启用供应商"
            />
          </div>
        </div>
      ) : null}
    </ProviderDialogShell>
  );
}

type SegmentedProps<T extends string> = {
  options: readonly { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
};

function Segmented<T extends string>({ options, value, onChange }: SegmentedProps<T>) {
  return (
    <div className="inline-flex flex-wrap gap-1 bg-muted p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={`px-3 py-1 text-sm transition-colors ${
            option.value === value
              ? "bg-background text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          }`}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function parseModelCodes(raw: string): string[] {
  return raw
    .split(/[\s,，]+/)
    .map((code) => code.trim())
    .filter((code) => code.length > 0);
}
