import { useEffect } from "react";
import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, RefreshCw, Settings2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormField } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { MultiSelect } from "../../library/components/multi-select";
import {
  type AiProviderProtocol,
  type AiSettingItem,
  patchAiSettingByName,
  resolveAiProviderApiError,
} from "../../providers/lib/api";
import { isForbiddenError, resolveGatewayApiError } from "../lib/api";
import { GATEWAY_UPSTREAM_PROTOCOL_OPTIONS } from "../lib/model";

type GatewaySetting = NonNullable<AiSettingItem["spec"]["gateway"]>;

type GatewaySettingsProps = {
  setting: AiSettingItem | undefined;
  loading: boolean;
  error: unknown;
  canEdit: boolean;
  onRetry: () => void;
};

const GATEWAY_SETTING_DEFAULTS: Required<GatewaySetting> = {
  rateLimitEnabled: true,
  maxRetries: 2,
  retryBackoffMs: 200,
  retryOnStatuses: [429, 500, 502, 503, 504],
  requestTimeoutSeconds: 120,
  streamIdleTimeoutSeconds: 120,
  injectionEnabled: true,
  memoryBridgeEnabled: true,
  supportedProtocols: ["OPENAI_CHAT", "ANTHROPIC"],
};

const nonNegativeInt = (message: string) =>
  z
    .string()
    .trim()
    .refine((value) => /^\d+$/.test(value), message);

const positiveInt = (message: string) =>
  z
    .string()
    .trim()
    .refine((value) => /^\d+$/.test(value) && Number(value) > 0, message);

const settingsSchema = z.object({
  rateLimitEnabled: z.boolean(),
  maxRetries: nonNegativeInt("最大重试必须是非负整数。"),
  retryBackoffMs: nonNegativeInt("重试退避必须是非负整数。"),
  retryOnStatuses: z
    .string()
    .trim()
    .refine((value) => {
      if (value === "") {
        return true;
      }
      return value
        .split(",")
        .map((part) => part.trim())
        .filter(Boolean)
        .every((part) => {
          if (!/^\d{3}$/.test(part)) {
            return false;
          }
          const status = Number(part);
          return status >= 100 && status <= 599;
        });
    }, "请输入 100–599 之间的状态码，用逗号分隔。"),
  requestTimeoutSeconds: positiveInt("请求超时必须是正整数。"),
  streamIdleTimeoutSeconds: positiveInt("流空闲超时必须是正整数。"),
  injectionEnabled: z.boolean(),
  memoryBridgeEnabled: z.boolean(),
  supportedProtocols: z.array(z.string()),
});

type GatewaySettingsFormValues = z.infer<typeof settingsSchema>;

function toFormValues(gateway: GatewaySetting | undefined): GatewaySettingsFormValues {
  const source = gateway ?? GATEWAY_SETTING_DEFAULTS;
  return {
    rateLimitEnabled: source.rateLimitEnabled ?? GATEWAY_SETTING_DEFAULTS.rateLimitEnabled,
    maxRetries: String(source.maxRetries ?? GATEWAY_SETTING_DEFAULTS.maxRetries),
    retryBackoffMs: String(source.retryBackoffMs ?? GATEWAY_SETTING_DEFAULTS.retryBackoffMs),
    retryOnStatuses: (source.retryOnStatuses ?? GATEWAY_SETTING_DEFAULTS.retryOnStatuses).join(
      ", ",
    ),
    requestTimeoutSeconds: String(
      source.requestTimeoutSeconds ?? GATEWAY_SETTING_DEFAULTS.requestTimeoutSeconds,
    ),
    streamIdleTimeoutSeconds: String(
      source.streamIdleTimeoutSeconds ?? GATEWAY_SETTING_DEFAULTS.streamIdleTimeoutSeconds,
    ),
    injectionEnabled: source.injectionEnabled ?? GATEWAY_SETTING_DEFAULTS.injectionEnabled,
    memoryBridgeEnabled: source.memoryBridgeEnabled ?? GATEWAY_SETTING_DEFAULTS.memoryBridgeEnabled,
    supportedProtocols: [
      ...(source.supportedProtocols ?? GATEWAY_SETTING_DEFAULTS.supportedProtocols),
    ],
  };
}

function parseStatuses(value: string): number[] {
  return [
    ...new Set(
      value
        .split(",")
        .map((part) => Number(part.trim()))
        .filter(Boolean),
    ),
  ].sort((left, right) => left - right);
}

function toGatewaySpec(values: GatewaySettingsFormValues): GatewaySetting {
  return {
    rateLimitEnabled: values.rateLimitEnabled,
    maxRetries: Number(values.maxRetries),
    retryBackoffMs: Number(values.retryBackoffMs),
    retryOnStatuses: parseStatuses(values.retryOnStatuses),
    requestTimeoutSeconds: Number(values.requestTimeoutSeconds),
    streamIdleTimeoutSeconds: Number(values.streamIdleTimeoutSeconds),
    injectionEnabled: values.injectionEnabled,
    memoryBridgeEnabled: values.memoryBridgeEnabled,
    supportedProtocols: values.supportedProtocols as AiProviderProtocol[],
  };
}

export function GatewaySettings({
  setting,
  loading,
  error,
  canEdit,
  onRetry,
}: GatewaySettingsProps) {
  const queryClient = useQueryClient();
  const form = useForm<GatewaySettingsFormValues>({
    resolver: zodResolver(settingsSchema) as Resolver<GatewaySettingsFormValues>,
    defaultValues: toFormValues(setting?.spec.gateway),
  });

  useEffect(() => {
    form.reset(toFormValues(setting?.spec.gateway));
  }, [form, setting]);

  const mutation = useMutation({
    mutationFn: async (values: GatewaySettingsFormValues) => {
      if (!setting) {
        throw new Error("AI 设置尚未加载。");
      }
      return patchAiSettingByName(setting.metadata.name, [
        {
          op: "add",
          path: "/spec/gateway",
          value: {
            ...(setting.spec.gateway ?? {}),
            ...toGatewaySpec(values),
          },
        },
      ]);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(["ai-settings", "ai-setting"], updated);
      form.reset(toFormValues(updated.spec.gateway));
      await queryClient.invalidateQueries({ queryKey: ["ai-gateway", "status"] });
      toast.success("网关设置已保存。");
    },
    onError: (mutationError) => {
      toast.error(
        isForbiddenError(mutationError)
          ? "没有保存网关设置的权限，请联系管理员授予“编辑网关设置”操作。"
          : resolveAiProviderApiError(mutationError),
      );
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="网关设置"
        description="限流、重试、超时、上下文注入与上游协议保存在 AI 设置的 gateway 段。"
        action={
          canEdit ? (
            <Badge variant="secondary">可编辑</Badge>
          ) : (
            <Badge variant="outline">需要授权</Badge>
          )
        }
      />

      {!canEdit ? (
        <div className="px-5 py-5 text-xs text-muted-foreground">
          当前账号没有编辑网关设置的权限，需要管理员授予“编辑网关设置”操作。
        </div>
      ) : loading ? (
        <div className="grid gap-3 px-5 py-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 9 }, (_, index) => (
            <Skeleton key={index} className="h-16" />
          ))}
        </div>
      ) : error || !setting ? (
        <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-5">
          <div className="space-y-1">
            <p className="text-sm font-medium">无法读取网关设置</p>
            <p className="text-xs text-muted-foreground">
              {error
                ? isForbiddenError(error)
                  ? "没有读取 AI 设置的权限。"
                  : resolveGatewayApiError(error)
                : "AI 设置中还没有可用的 gateway 配置。"}
            </p>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={onRetry}>
            <RefreshCw />
            重试
          </Button>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="space-y-6 px-5 py-5">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <Controller
              control={form.control}
              name="rateLimitEnabled"
              render={({ field }) => (
                <SwitchRow
                  id="gateway-setting-rate-limit"
                  label="限流"
                  description="启用网关限流与密钥级预算检查。"
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              )}
            />
            <Controller
              control={form.control}
              name="injectionEnabled"
              render={({ field }) => (
                <SwitchRow
                  id="gateway-setting-injection"
                  label="上下文注入"
                  description="允许网关请求携带平台上下文。"
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              )}
            />
            <Controller
              control={form.control}
              name="memoryBridgeEnabled"
              render={({ field }) => (
                <SwitchRow
                  id="gateway-setting-memory-bridge"
                  label="记忆桥接"
                  description="启用网关与记忆作用域之间的桥接。"
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              )}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <FormField
              label="最大重试"
              htmlFor="gateway-setting-max-retries"
              hint="0 表示不重试。"
              error={form.formState.errors.maxRetries?.message}
            >
              <Input
                id="gateway-setting-max-retries"
                type="number"
                min={0}
                {...form.register("maxRetries")}
              />
            </FormField>
            <FormField
              label="重试退避"
              htmlFor="gateway-setting-retry-backoff"
              hint="单位：毫秒。"
              error={form.formState.errors.retryBackoffMs?.message}
            >
              <Input
                id="gateway-setting-retry-backoff"
                type="number"
                min={0}
                {...form.register("retryBackoffMs")}
              />
            </FormField>
            <FormField
              label="请求超时"
              htmlFor="gateway-setting-request-timeout"
              hint="单位：秒。"
              error={form.formState.errors.requestTimeoutSeconds?.message}
            >
              <Input
                id="gateway-setting-request-timeout"
                type="number"
                min={1}
                {...form.register("requestTimeoutSeconds")}
              />
            </FormField>
            <FormField
              label="流空闲超时"
              htmlFor="gateway-setting-stream-idle-timeout"
              hint="单位：秒。"
              error={form.formState.errors.streamIdleTimeoutSeconds?.message}
            >
              <Input
                id="gateway-setting-stream-idle-timeout"
                type="number"
                min={1}
                {...form.register("streamIdleTimeoutSeconds")}
              />
            </FormField>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <FormField
              label="重试状态码"
              htmlFor="gateway-setting-retry-statuses"
              hint="用逗号分隔，例如 429, 500, 502；留空表示不按状态码重试。"
              error={form.formState.errors.retryOnStatuses?.message}
            >
              <Input
                id="gateway-setting-retry-statuses"
                placeholder="429, 500, 502, 503, 504"
                {...form.register("retryOnStatuses")}
              />
            </FormField>
            <FormField
              label="上游协议"
              htmlFor="gateway-setting-protocols"
              hint="声明网关接受的上游协议；实际模型仍受供应商与路由组约束。"
            >
              <Controller
                control={form.control}
                name="supportedProtocols"
                render={({ field }) => (
                  <MultiSelect
                    id="gateway-setting-protocols"
                    value={field.value}
                    onChange={field.onChange}
                    options={GATEWAY_UPSTREAM_PROTOCOL_OPTIONS}
                    placeholder="选择协议"
                    emptyMessage="没有可选项"
                    searchPlaceholder="搜索协议"
                  />
                )}
              />
            </FormField>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
            <p className="text-[11px] text-muted-foreground">
              LAN、TLS、mTLS 由部署配置管理，页面不提供运行期开关。
            </p>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => form.reset(toFormValues(setting.spec.gateway))}
                disabled={!form.formState.isDirty || mutation.isPending}
              >
                重置
              </Button>
              <Button type="submit" disabled={!form.formState.isDirty || mutation.isPending}>
                {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
                保存设置
              </Button>
            </div>
          </div>
        </form>
      )}

      <div className="flex items-center gap-2 border-t px-5 py-3 text-[11px] text-muted-foreground">
        <Settings2 className="size-3.5" />
        设置通过 PUT /aisettings/ai-setting 保存，仅供网关运行期使用。
      </div>
    </ConsoleSection>
  );
}

function SwitchRow({
  id,
  label,
  description,
  checked,
  onCheckedChange,
}: {
  id: string;
  label: string;
  description: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}) {
  return (
    <div className="flex items-start justify-between gap-3 border px-3 py-3">
      <div className="space-y-1">
        <Label htmlFor={id}>{label}</Label>
        <p className="text-[11px] text-muted-foreground">{description}</p>
      </div>
      <Switch id={id} checked={checked} onCheckedChange={onCheckedChange} />
    </div>
  );
}
