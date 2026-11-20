import { useEffect } from "react";
import { Controller, type Resolver, useFieldArray, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormField } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";

import { MultiSelect } from "../../library/components/multi-select";
import type { AiModelItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import {
  GATEWAY_KEY_LIMIT_MAX,
  GATEWAY_TOKEN_DEFAULT_DAYS,
  GATEWAY_TOKEN_ROLE,
  type GatewayKeySpec,
  type GatewayServiceTier,
  type GatewayTokenCreation,
  createGatewayToken,
  isHttpStatus,
  resolveGatewayApiError,
} from "../lib/api";
import { GATEWAY_LIMIT_PERIOD_OPTIONS, GATEWAY_SERVICE_TIER_OPTIONS } from "../lib/model";

const numericText = (message: string) =>
  z
    .string()
    .trim()
    .refine((value) => value === "" || /^\d+$/.test(value), message);

const limitSchema = z
  .object({
    period: z.enum(["DAY", "WEEK", "MONTH", "TOTAL"]),
    tokens: numericText("Token 上限必须是非负整数。"),
    units: numericText("标准 Token 上限必须是非负整数。"),
    requests: numericText("请求数上限必须是非负整数。"),
    models: z.array(z.string()),
    account: z.string().trim(),
  })
  .superRefine((limit, context) => {
    if (limit.tokens === "" && limit.units === "" && limit.requests === "") {
      context.addIssue({
        code: "custom",
        path: ["tokens"],
        message: "每条限制至少填写 Token、标准 Token 或请求数之一。",
      });
    }
  });

const keySchema = z.object({
  name: z.string().trim().min(1, "请输入密钥名称。").max(100, "密钥名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "说明不能超过 500 个字符。"),
  expiryDays: z
    .string()
    .trim()
    .min(1, "请输入到期天数。")
    .refine((value) => /^\d+$/.test(value) && Number(value) > 0, "到期天数必须是正整数。"),
  gatewayEnabled: z.boolean(),
  gatewayModels: z.array(z.string()),
  maxConcurrency: numericText("最大并发必须是非负整数。"),
  serviceTier: z.enum(["STANDARD", "PRIORITY", "ULTRAFAST"]),
  limits: z
    .array(limitSchema)
    .max(GATEWAY_KEY_LIMIT_MAX, `最多配置 ${GATEWAY_KEY_LIMIT_MAX} 条限制。`),
});

type GatewayKeyFormValues = z.infer<typeof keySchema>;

type GatewayKeyDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  models: AiModelItem[];
  advancedSupported: boolean;
  onCreated: (result: GatewayTokenCreation) => void;
};

function createDefaults(): GatewayKeyFormValues {
  return {
    name: "",
    description: "",
    expiryDays: String(GATEWAY_TOKEN_DEFAULT_DAYS),
    gatewayEnabled: true,
    gatewayModels: [],
    maxConcurrency: "",
    serviceTier: "STANDARD",
    limits: [],
  };
}

function buildGatewaySpec(values: GatewayKeyFormValues): GatewayKeySpec {
  return {
    enabled: values.gatewayEnabled,
    models: values.gatewayModels,
    maxConcurrency: values.maxConcurrency === "" ? undefined : Number(values.maxConcurrency),
    serviceTier: values.serviceTier,
    limits: values.limits.map((limit) => ({
      period: limit.period,
      tokens: limit.tokens === "" ? undefined : Number(limit.tokens),
      units: limit.units === "" ? undefined : Number(limit.units),
      requests: limit.requests === "" ? undefined : Number(limit.requests),
      models: limit.models,
      account: limit.account.trim() || undefined,
    })),
  };
}

export function GatewayKeyDialog({
  open,
  onOpenChange,
  models,
  advancedSupported,
  onCreated,
}: GatewayKeyDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<GatewayKeyFormValues>({
    resolver: zodResolver(keySchema) as Resolver<GatewayKeyFormValues>,
    defaultValues: createDefaults(),
  });
  const limits = useFieldArray({ control: form.control, name: "limits" });
  const modelOptions = models.map((model) => ({
    value: model.metadata.name,
    label: modelDisplayName(model),
    hint: model.spec.modelCode,
  }));

  useEffect(() => {
    if (open) {
      form.reset(createDefaults());
    }
  }, [form, open]);

  const mutation = useMutation({
    mutationFn: (values: GatewayKeyFormValues) =>
      createGatewayToken({
        name: values.name.trim(),
        description: values.description.trim() || undefined,
        expiryDays: Number(values.expiryDays),
        ...(advancedSupported ? { gateway: buildGatewaySpec(values) } : {}),
      }),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-gateway", "tokens"] });
      onCreated(result);
      if (!result.gatewayApplied) {
        toast.warning("密钥已创建，但后端没有保存密钥级网关限制；请确认后端版本或改用基础密钥。");
      } else {
        toast.success(
          result.created.token
            ? "网关密钥已生成，请立即复制保存。"
            : "网关密钥已生成，但响应中没有返回一次性明文。",
        );
      }
      onOpenChange(false);
    },
    onError: (error) => {
      if (isHttpStatus(error, 403)) {
        toast.error("没有创建网关密钥的权限，或当前账号缺少 AI Agent 网关角色。");
        return;
      }
      const message = resolveGatewayApiError(error);
      toast.error(
        message.includes("授予") || message.includes("角色")
          ? "当前账号没有 AI Agent 网关角色，请让管理员授予 role-template-ai-gateway 后再创建。"
          : message,
      );
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );
  const limitsError =
    form.formState.errors.limits?.message ?? form.formState.errors.limits?.root?.message;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!mutation.isPending) {
          onOpenChange(next);
        }
      }}
    >
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>生成网关密钥</DialogTitle>
          <DialogDescription>
            密钥复用个人访问令牌，并固定绑定 AI Agent 网关角色。明文只在创建成功后显示一次。
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="space-y-4">
          <div className="max-h-[62vh] space-y-5 overflow-y-auto pe-1">
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="sm:col-span-2">
                <FormField
                  label="名称"
                  htmlFor="gateway-key-name"
                  error={form.formState.errors.name?.message}
                >
                  <Input
                    id="gateway-key-name"
                    placeholder="例如：Claude Code 笔记本"
                    autoComplete="off"
                    {...form.register("name")}
                  />
                </FormField>
              </div>
              <FormField
                label="到期天数"
                htmlFor="gateway-key-expiry"
                hint={`默认 ${GATEWAY_TOKEN_DEFAULT_DAYS} 天`}
                error={form.formState.errors.expiryDays?.message}
              >
                <Input
                  id="gateway-key-expiry"
                  type="number"
                  min={1}
                  max={3650}
                  {...form.register("expiryDays")}
                />
              </FormField>
            </div>

            <FormField
              label="说明"
              htmlFor="gateway-key-description"
              error={form.formState.errors.description?.message}
            >
              <Textarea
                id="gateway-key-description"
                rows={2}
                placeholder="用途、使用设备或负责人"
                {...form.register("description")}
              />
            </FormField>

            <div className="rounded-lg border bg-muted/25 px-4 py-3">
              <p className="text-xs text-muted-foreground">固定权限</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <Badge variant="secondary">{GATEWAY_TOKEN_ROLE}</Badge>
                <Badge variant="outline">RolesOnly</Badge>
              </div>
              <p className="mt-2 text-[11px] text-muted-foreground">
                该令牌只能调用 AI 网关，不会继承你的其他角色；后端返回一次性明文后请立即保存。
              </p>
            </div>

            {advancedSupported ? (
              <div className="space-y-4 rounded-lg border px-4 py-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="space-y-1">
                    <p className="text-sm font-medium">密钥级网关限制</p>
                    <p className="text-xs text-muted-foreground">
                      限制该密钥可用的模型、并发、服务层级与预算；留空表示继承网关默认值。
                    </p>
                  </div>
                  <Controller
                    control={form.control}
                    name="gatewayEnabled"
                    render={({ field }) => (
                      <div className="flex items-center gap-2">
                        <Label htmlFor="gateway-key-enabled" className="text-xs">
                          启用密钥
                        </Label>
                        <Switch
                          id="gateway-key-enabled"
                          checked={field.value}
                          onCheckedChange={field.onChange}
                        />
                      </div>
                    )}
                  />
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <FormField label="可用模型" htmlFor="gateway-key-models">
                    <Controller
                      control={form.control}
                      name="gatewayModels"
                      render={({ field }) => (
                        <MultiSelect
                          id="gateway-key-models"
                          value={field.value}
                          onChange={field.onChange}
                          options={modelOptions}
                          placeholder="全部可用模型"
                          emptyMessage="没有可用模型"
                          searchPlaceholder="搜索模型"
                        />
                      )}
                    />
                  </FormField>
                  <FormField
                    label="最大并发"
                    htmlFor="gateway-key-concurrency"
                    hint="留空表示不限制。"
                    error={form.formState.errors.maxConcurrency?.message}
                  >
                    <Input
                      id="gateway-key-concurrency"
                      type="number"
                      min={0}
                      placeholder="不限制"
                      {...form.register("maxConcurrency")}
                    />
                  </FormField>
                  <FormField label="服务层级" htmlFor="gateway-key-tier">
                    <Controller
                      control={form.control}
                      name="serviceTier"
                      render={({ field }) => (
                        <Select
                          value={field.value}
                          onValueChange={(value) => field.onChange(value as GatewayServiceTier)}
                        >
                          <SelectTrigger id="gateway-key-tier" className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {GATEWAY_SERVICE_TIER_OPTIONS.map((option) => (
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

                <div className="space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="space-y-0.5">
                      <p className="text-sm font-medium">用量限制</p>
                      <p className="text-[11px] text-muted-foreground">
                        最多 {GATEWAY_KEY_LIMIT_MAX} 条；按指定周期聚合 Token、标准 Token 或请求数。
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        limits.append({
                          period: "DAY",
                          tokens: "",
                          units: "",
                          requests: "",
                          models: [],
                          account: "",
                        })
                      }
                      disabled={limits.fields.length >= GATEWAY_KEY_LIMIT_MAX}
                    >
                      <Plus />
                      添加限制
                    </Button>
                  </div>
                  {limitsError ? <p className="text-xs text-destructive">{limitsError}</p> : null}

                  {limits.fields.length === 0 ? (
                    <p className="rounded-lg border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
                      未配置用量限制。
                    </p>
                  ) : (
                    <div className="space-y-3">
                      {limits.fields.map((field, index) => {
                        const itemErrors = form.formState.errors.limits?.[index];
                        return (
                          <div key={field.id} className="space-y-3 rounded-lg border p-3">
                            <div className="flex items-center justify-between gap-2">
                              <p className="text-xs font-medium">限制 {index + 1}</p>
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`移除限制 ${index + 1}`}
                                onClick={() => limits.remove(index)}
                              >
                                <Trash2 />
                              </Button>
                            </div>
                            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                              <FormField label="周期" htmlFor={`gateway-limit-period-${index}`}>
                                <Controller
                                  control={form.control}
                                  name={`limits.${index}.period`}
                                  render={({ field: periodField }) => (
                                    <Select
                                      value={periodField.value}
                                      onValueChange={periodField.onChange}
                                    >
                                      <SelectTrigger
                                        id={`gateway-limit-period-${index}`}
                                        className="w-full"
                                      >
                                        <SelectValue />
                                      </SelectTrigger>
                                      <SelectContent>
                                        {GATEWAY_LIMIT_PERIOD_OPTIONS.map((option) => (
                                          <SelectItem key={option.value} value={option.value}>
                                            {option.label}
                                          </SelectItem>
                                        ))}
                                      </SelectContent>
                                    </Select>
                                  )}
                                />
                              </FormField>
                              <FormField
                                label="Token"
                                htmlFor={`gateway-limit-tokens-${index}`}
                                error={
                                  itemErrors?.tokens?.message ??
                                  itemErrors?.units?.message ??
                                  itemErrors?.requests?.message
                                }
                              >
                                <Input
                                  id={`gateway-limit-tokens-${index}`}
                                  type="number"
                                  min={0}
                                  placeholder="不限制"
                                  {...form.register(`limits.${index}.tokens`)}
                                />
                              </FormField>
                              <FormField
                                label="标准 Token"
                                htmlFor={`gateway-limit-units-${index}`}
                              >
                                <Input
                                  id={`gateway-limit-units-${index}`}
                                  type="number"
                                  min={0}
                                  placeholder="不限制"
                                  {...form.register(`limits.${index}.units`)}
                                />
                              </FormField>
                              <FormField label="请求数" htmlFor={`gateway-limit-requests-${index}`}>
                                <Input
                                  id={`gateway-limit-requests-${index}`}
                                  type="number"
                                  min={0}
                                  placeholder="不限制"
                                  {...form.register(`limits.${index}.requests`)}
                                />
                              </FormField>
                            </div>
                            <div className="grid gap-3 sm:grid-cols-2">
                              <FormField label="限定模型" htmlFor={`gateway-limit-models-${index}`}>
                                <Controller
                                  control={form.control}
                                  name={`limits.${index}.models`}
                                  render={({ field: modelsField }) => (
                                    <MultiSelect
                                      id={`gateway-limit-models-${index}`}
                                      value={modelsField.value}
                                      onChange={modelsField.onChange}
                                      options={modelOptions}
                                      placeholder="全部模型"
                                      emptyMessage="没有可用模型"
                                      searchPlaceholder="搜索模型"
                                    />
                                  )}
                                />
                              </FormField>
                              <FormField
                                label="限定账号"
                                htmlFor={`gateway-limit-account-${index}`}
                                hint="留空表示全部账号。"
                              >
                                <Input
                                  id={`gateway-limit-account-${index}`}
                                  placeholder="供应商账号资源名"
                                  autoComplete="off"
                                  {...form.register(`limits.${index}.account`)}
                                />
                              </FormField>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            ) : null}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              取消
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
              生成密钥
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
