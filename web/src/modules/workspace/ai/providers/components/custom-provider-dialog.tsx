import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Boxes } from "lucide-react";
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
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";

import {
  AI_API_VERSION,
  AI_MODEL_KIND,
  AI_PROVIDER_ACCOUNT_KIND,
  AI_PROVIDER_KIND,
  type AiProviderProtocol,
  createModel,
  createProvider,
  createProviderAccount,
  resolveAiProviderApiError,
} from "../lib/api";
import {
  DEFAULT_PROVIDER_SPEC,
  PROTOCOL_OPTIONS,
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
  defaultBaseUrlFor,
  modelNameFromCode,
} from "../lib/model";
import type { ProviderPreset } from "../lib/presets";

const PROTOCOL_VALUES = PROTOCOL_OPTIONS.map((option) => option.value) as [
  AiProviderProtocol,
  ...AiProviderProtocol[],
];

const customProviderSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入 ID。")
    .max(63, "ID 不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  protocol: z.enum(PROTOCOL_VALUES),
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
  onClose: () => void;
};

export function CustomProviderDialog({ preset, onClose }: CustomProviderDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<CustomProviderFormValues>({
    resolver: zodResolver(customProviderSchema) as Resolver<CustomProviderFormValues>,
    defaultValues: {
      name: preset?.id ?? "",
      displayName: preset?.displayName ?? "",
      protocol: preset?.protocol ?? DEFAULT_PROVIDER_SPEC.protocol,
      baseUrl: preset?.baseUrl ?? defaultBaseUrlFor(DEFAULT_PROVIDER_SPEC.protocol),
      apiKey: "",
      modelCodes: "",
      enabled: true,
    },
  });
  const protocol = useWatch({ control: form.control, name: "protocol" });
  const enabled = useWatch({ control: form.control, name: "enabled" });

  const mutation = useMutation({
    mutationFn: async (values: CustomProviderFormValues) => {
      await createProvider({
        apiVersion: AI_API_VERSION,
        kind: AI_PROVIDER_KIND,
        metadata: { name: values.name },
        spec: {
          displayName: values.displayName,
          protocol: values.protocol,
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

      return { accountCreated, modelCount, failures };
    },
    onSuccess: async ({ accountCreated, modelCount, failures }) => {
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
      onClose();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Boxes />}
      title={preset ? `添加 ${preset.displayName}` : "自定义供应商"}
      description="填写供应商地址和密钥，保存后可在详情页自动获取模型。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      submitLabel="添加供应商"
      maxHeightRatio={0.6}
    >
      <FormDialogSection title="基础信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="ai-custom-provider-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input id="ai-custom-provider-display-name" {...form.register("displayName")} />
          </FormField>
          <FormField
            label="ID"
            htmlFor="ai-custom-provider-name"
            hint={RESOURCE_NAME_HINT}
            error={form.formState.errors.name?.message}
          >
            <Input id="ai-custom-provider-name" {...form.register("name")} />
          </FormField>
          <FormField
            label="协议"
            htmlFor="ai-custom-provider-protocol"
            error={form.formState.errors.protocol?.message}
          >
            <Select
              value={protocol}
              onValueChange={(value) => {
                const nextProtocol = value as AiProviderProtocol;
                const currentBaseUrl = form.getValues("baseUrl").trim();
                if (!currentBaseUrl || currentBaseUrl === defaultBaseUrlFor(protocol)) {
                  form.setValue("baseUrl", defaultBaseUrlFor(nextProtocol), {
                    shouldDirty: true,
                  });
                }
                form.setValue("protocol", nextProtocol, { shouldDirty: true });
              }}
            >
              <SelectTrigger id="ai-custom-provider-protocol" className="w-full">
                <SelectValue placeholder="选择协议" />
              </SelectTrigger>
              <SelectContent>
                {PROTOCOL_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
          <FormField
            label="Base URL"
            htmlFor="ai-custom-provider-base-url"
            hint="调用供应商接口的根地址。"
            error={form.formState.errors.baseUrl?.message}
          >
            <Input
              id="ai-custom-provider-base-url"
              placeholder="https://api.example.com"
              {...form.register("baseUrl")}
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="密钥与模型">
        <FormField
          label="API 密钥"
          htmlFor="ai-custom-provider-api-key"
          hint="可选；保存后会创建一个启用账号。"
          error={form.formState.errors.apiKey?.message}
        >
          <Input
            id="ai-custom-provider-api-key"
            type="password"
            autoComplete="new-password"
            placeholder="粘贴 API 密钥"
            {...form.register("apiKey")}
          />
        </FormField>
        <FormField
          label="模型 ID"
          htmlFor="ai-custom-provider-models"
          hint="可选；多个模型用逗号或换行分隔，保存后创建为对话模型。"
          error={form.formState.errors.modelCodes?.message}
        >
          <Textarea
            id="ai-custom-provider-models"
            rows={3}
            placeholder="deepseek-chat, deepseek-reasoner"
            {...form.register("modelCodes")}
          />
        </FormField>
        <div className="flex items-center justify-between gap-4 rounded-lg border p-4">
          <div className="space-y-1">
            <p className="text-sm font-medium">启用供应商</p>
            <p className="text-xs text-muted-foreground">关闭后不参与路由。</p>
          </div>
          <Switch
            checked={enabled}
            onCheckedChange={(checked) => form.setValue("enabled", checked, { shouldDirty: true })}
            aria-label="启用供应商"
          />
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}

function parseModelCodes(raw: string): string[] {
  return raw
    .split(/[\s,，]+/)
    .map((code) => code.trim())
    .filter((code) => code.length > 0);
}
