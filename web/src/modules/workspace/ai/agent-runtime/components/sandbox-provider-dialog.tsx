import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ServerCog } from "lucide-react";
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
import { resourceGenerateName } from "@/modules/workspace/ai/library/lib/model";

import {
  AI_API_VERSION,
  AI_SANDBOX_PROVIDER_KIND,
  type JsonPatchOperation,
  type SandboxProviderItem,
  type SandboxProviderSpec,
  createSandboxProvider,
  patchSandboxProvider,
  resolveAgentRuntimeError,
} from "../lib/api";
import { SANDBOX_PROVIDER_OPTIONS } from "../lib/model";

const sandboxProviderSchema = z.object({
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  provider: z.enum(["ALIYUN", "TENCENT"]),
  endpoint: z.string().trim().max(1000, "接入地址过长。"),
  region: z.string().trim().min(1, "请输入区域。").max(100, "区域名称过长。"),
  accessKeyId: z.string().trim().min(1, "请输入 AccessKey ID。").max(200, "AccessKey ID 过长。"),
  accessKeySecret: z.string().max(1000, "AccessKey Secret 过长。"),
  defaultImage: z.string().trim().min(1, "请输入默认镜像。").max(500, "镜像名称过长。"),
  maxTimeoutSeconds: z.coerce
    .number()
    .int("最大超时必须是整数。")
    .min(60, "最大超时至少为 60 秒。")
    .max(86400, "最大超时不能超过 86400 秒。"),
  enabled: z.boolean(),
});

type SandboxProviderFormValues = z.infer<typeof sandboxProviderSchema>;

type SandboxProviderDialogProps = {
  provider?: SandboxProviderItem;
  onClose: () => void;
};

export function SandboxProviderDialog({ provider, onClose }: SandboxProviderDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<SandboxProviderFormValues>({
    resolver: zodResolver(sandboxProviderSchema) as Resolver<SandboxProviderFormValues>,
    defaultValues: toFormValues(provider),
  });
  const providerName = useWatch({ control: form.control, name: "provider" });

  const saveMutation = useMutation({
    mutationFn: (values: SandboxProviderFormValues) => {
      if (provider) {
        const operations = buildPatchOperations(provider, values);
        return operations.length === 0
          ? Promise.resolve(provider)
          : patchSandboxProvider(provider.metadata.name, operations);
      }
      return createSandboxProvider({
        apiVersion: AI_API_VERSION,
        kind: AI_SANDBOX_PROVIDER_KIND,
        metadata: { generateName: resourceGenerateName(values.displayName, "sandbox") },
        spec: toCreateSpec(values),
      });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["ai-sandbox-providers"] }),
        queryClient.invalidateQueries({ queryKey: ["ai-runtime-providers"] }),
      ]);
      toast.success(provider ? "沙箱提供方已保存。" : "沙箱提供方已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveAgentRuntimeError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => {
      if (!provider && !values.accessKeySecret.trim()) {
        form.setError("accessKeySecret", { message: "首次配置必须填写 AccessKey Secret。" });
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
      icon={<ServerCog className="size-5" />}
      title={
        provider ? `编辑 ${provider.spec.displayName || provider.metadata.name}` : "新增沙箱提供方"
      }
      description="配置云端沙箱接入地址与凭据；密钥只写，保存后不会再次返回。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-2xl"
      maxHeightRatio={0.7}
      submitLabel={provider ? "保存更改" : "创建提供方"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="sandbox-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="sandbox-display-name"
              placeholder="例如：阿里云 AgentBay"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="提供方"
            htmlFor="sandbox-provider"
            hint={
              providerName === "TENCENT"
                ? "腾讯云使用云沙箱接口，区域用于签名与默认地址。"
                : "阿里云使用 AgentBay 接口，区域用于拼接默认地址。"
            }
            error={form.formState.errors.provider?.message}
          >
            <Controller
              control={form.control}
              name="provider"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as "ALIYUN" | "TENCENT")}
                >
                  <SelectTrigger id="sandbox-provider" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SANDBOX_PROVIDER_OPTIONS.map((option) => (
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
        <Controller
          control={form.control}
          name="enabled"
          render={({ field }) => (
            <div className="flex items-center justify-between gap-4 border p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">启用</p>
                <p className="text-xs text-muted-foreground">
                  停用后使用该提供方的 Agent 无法启动运行时。
                </p>
              </div>
              <Switch
                checked={field.value}
                onCheckedChange={field.onChange}
                disabled={saveMutation.isPending}
                aria-label="启用沙箱提供方"
              />
            </div>
          )}
        />
      </FormDialogSection>

      <FormDialogSection title="访问凭据">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="AccessKey ID"
            htmlFor="sandbox-access-key-id"
            error={form.formState.errors.accessKeyId?.message}
          >
            <Input
              id="sandbox-access-key-id"
              className="font-mono"
              autoComplete="off"
              {...form.register("accessKeyId")}
            />
          </FormField>
          <FormField
            label="AccessKey Secret"
            htmlFor="sandbox-access-key-secret"
            hint={provider ? "留空表示保留现有密钥，仅在轮换时输入。" : "创建时必须填写。"}
            error={form.formState.errors.accessKeySecret?.message}
          >
            <Input
              id="sandbox-access-key-secret"
              type="password"
              className="font-mono"
              autoComplete="new-password"
              placeholder={provider ? "留空保留现有密钥" : "输入 AccessKey Secret"}
              {...form.register("accessKeySecret")}
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="接入参数">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="区域"
            htmlFor="sandbox-region"
            hint="例如 cn-hangzhou；用于默认接入地址。"
            error={form.formState.errors.region?.message}
          >
            <Input
              id="sandbox-region"
              className="font-mono"
              placeholder="cn-hangzhou"
              {...form.register("region")}
            />
          </FormField>
          <FormField
            label="最大超时（秒）"
            htmlFor="sandbox-max-timeout"
            hint="Agent 与命令超时会被限制在该时长内。"
            error={form.formState.errors.maxTimeoutSeconds?.message}
          >
            <Input
              id="sandbox-max-timeout"
              type="number"
              inputMode="numeric"
              {...form.register("maxTimeoutSeconds")}
            />
          </FormField>
        </div>
        <FormField
          label="接入地址"
          htmlFor="sandbox-endpoint"
          hint="留空使用区域对应的默认地址。"
          error={form.formState.errors.endpoint?.message}
        >
          <Input
            id="sandbox-endpoint"
            className="font-mono"
            placeholder="https://"
            {...form.register("endpoint")}
          />
        </FormField>
        <FormField
          label="默认镜像"
          htmlFor="sandbox-default-image"
          hint="Agent 未配置镜像时使用；腾讯云可填 ToolId 或 ToolName。"
          error={form.formState.errors.defaultImage?.message}
        >
          <Input
            id="sandbox-default-image"
            className="font-mono"
            placeholder="镜像 ID 或工具名称"
            {...form.register("defaultImage")}
          />
        </FormField>
      </FormDialogSection>
    </FormDialog>
  );
}

function toFormValues(provider: SandboxProviderItem | undefined): SandboxProviderFormValues {
  return {
    displayName: provider?.spec.displayName ?? "",
    provider: provider?.spec.provider ?? "ALIYUN",
    endpoint: provider?.spec.endpoint ?? "",
    region: provider?.spec.region ?? "",
    accessKeyId: provider?.spec.accessKeyId ?? "",
    accessKeySecret: "",
    defaultImage: provider?.spec.defaultImage ?? "",
    maxTimeoutSeconds: provider?.spec.maxTimeoutSeconds ?? 3600,
    enabled: provider?.spec.enabled !== false,
  };
}

function toCreateSpec(values: SandboxProviderFormValues): SandboxProviderSpec {
  return {
    provider: values.provider,
    displayName: values.displayName.trim(),
    endpoint: values.endpoint.trim() || undefined,
    region: values.region.trim(),
    accessKeyId: values.accessKeyId.trim(),
    accessKeySecret: values.accessKeySecret.trim(),
    defaultImage: values.defaultImage.trim(),
    maxTimeoutSeconds: values.maxTimeoutSeconds,
    enabled: values.enabled,
  };
}

function buildPatchOperations(
  provider: SandboxProviderItem,
  values: SandboxProviderFormValues,
): JsonPatchOperation[] {
  const spec = provider.spec;
  const fields = [
    { path: "/spec/provider", current: spec.provider, next: values.provider },
    {
      path: "/spec/displayName",
      current: spec.displayName ?? "",
      next: values.displayName.trim(),
    },
    {
      path: "/spec/endpoint",
      current: spec.endpoint ?? null,
      next: values.endpoint.trim() || null,
    },
    { path: "/spec/region", current: spec.region ?? "", next: values.region.trim() },
    {
      path: "/spec/accessKeyId",
      current: spec.accessKeyId ?? "",
      next: values.accessKeyId.trim(),
    },
    {
      path: "/spec/defaultImage",
      current: spec.defaultImage ?? "",
      next: values.defaultImage.trim(),
    },
    {
      path: "/spec/maxTimeoutSeconds",
      current: spec.maxTimeoutSeconds ?? 3600,
      next: values.maxTimeoutSeconds,
    },
    { path: "/spec/enabled", current: spec.enabled !== false, next: values.enabled },
  ];
  const operations: JsonPatchOperation[] = fields
    .filter((field) => field.current !== field.next)
    .map((field) => ({ op: "add", path: field.path, value: field.next }));
  const secret = values.accessKeySecret.trim();
  if (secret) {
    operations.push({ op: "add", path: "/spec/accessKeySecret", value: secret });
  }
  return operations;
}
