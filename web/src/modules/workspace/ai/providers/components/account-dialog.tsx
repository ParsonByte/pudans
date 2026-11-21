import type { ReactNode } from "react";
import {
  type Resolver,
  type UseFormReturn,
  useFieldArray,
  useForm,
  useWatch,
} from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { beijingDateTimeInputToIso, beijingDateTimeInputValue } from "@/lib/date";

import {
  AI_API_VERSION,
  AI_PROVIDER_ACCOUNT_KIND,
  type AiAccountType,
  type AiProviderAccountBalanceProbe,
  type AiProviderAccountInput,
  type AiProviderAccountItem,
  type AiProviderItem,
  type AiQuotaUnit,
  type JsonPatchOperation,
  createProviderAccount,
  fetchProviderAccount,
  patchProviderAccount,
  resolveAiProviderApiError,
} from "../lib/api";
import {
  ACCOUNT_TYPE_OPTIONS,
  DEFAULT_ACCOUNT_SPEC,
  QUOTA_UNIT_OPTIONS,
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "../lib/model";

const ACCOUNT_TYPE_VALUES = ACCOUNT_TYPE_OPTIONS.map((option) => option.value) as [
  AiAccountType,
  ...AiAccountType[],
];

const QUOTA_UNIT_VALUES = QUOTA_UNIT_OPTIONS.map((option) => option.value) as [
  AiQuotaUnit,
  ...AiQuotaUnit[],
];

const NON_NEGATIVE_PATTERN = /^\d+(\.\d+)?$/;

const windowSchema = z.object({
  name: z.string().trim().max(50, "窗口名称不能超过 50 个字符。"),
  unit: z.enum(QUOTA_UNIT_VALUES),
  limit: z
    .string()
    .trim()
    .refine((value) => value === "" || NON_NEGATIVE_PATTERN.test(value), "请输入非负数字。"),
  used: z
    .string()
    .trim()
    .refine((value) => value === "" || NON_NEGATIVE_PATTERN.test(value), "请输入非负数字。"),
  resetsAt: z
    .string()
    .trim()
    .refine(
      (value) => value === "" || !Number.isNaN(new Date(value).getTime()),
      "请输入有效的重置时间。",
    ),
});

const concurrencySchema = z.object({
  model: z.string().trim().max(200, "模型编码过长。"),
  value: z
    .string()
    .trim()
    .refine((value) => value === "" || /^\d+$/.test(value), "并发数必须为非负整数。"),
});

const accountSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "请输入名称。")
      .max(63, "名称不能超过 63 个字符。")
      .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
    displayName: z.string().trim().max(100, "显示名称不能超过 100 个字符。"),
    accountType: z.enum(ACCOUNT_TYPE_VALUES),
    apiKey: z.string().trim().max(1000, "密钥过长。"),
    keyName: z.string().trim().max(100, "密钥名称不能超过 100 个字符。"),
    keyPlan: z.string().trim().max(100, "套餐名称不能超过 100 个字符。"),
    spendCapPoints: z
      .string()
      .trim()
      .refine((value) => value === "" || NON_NEGATIVE_PATTERN.test(value), "请输入非负数字。"),
    weight: z.coerce.number().int().min(1, "权重至少为 1。").max(1000, "权重不能超过 1000。"),
    priority: z.coerce.number().int().min(0, "优先级不能为负数。").max(1000),
    maxConcurrency: z.coerce.number().int().min(1, "并发数至少为 1。").max(100000),
    maxRPM: z.coerce.number().int().min(0, "每分钟请求数不能为负数。").max(1000000),
    enabled: z.boolean(),
    freePlan: z.boolean(),
    creditsEnabled: z.boolean(),
    windows: z.array(windowSchema).max(24, "最多配置 24 个额度窗口。"),
    concurrencyByModel: z.array(concurrencySchema).max(200, "最多配置 200 个模型并发。"),
    balanceProbeUrl: z.string().trim().max(500, "地址不能超过 500 个字符。"),
    balanceProbePath: z.string().trim().max(200, "路径不能超过 200 个字符。"),
    balanceProbeMethod: z.string().trim().max(20, "方法过长。"),
    balanceProbeToken: z.string().trim().max(1000, "令牌过长。"),
    balanceProbeHeaderName: z.string().trim().max(100, "请求头名称过长。"),
  })
  .superRefine((values, context) => {
    const models = new Set<string>();
    values.concurrencyByModel.forEach((row, index) => {
      const model = row.model.trim();
      if (!model) {
        return;
      }
      if (models.has(model)) {
        context.addIssue({
          code: "custom",
          path: ["concurrencyByModel", index, "model"],
          message: "模型编码重复。",
        });
      }
      models.add(model);
    });
    const windows = new Set<string>();
    values.windows.forEach((window, index) => {
      const name = window.name.trim();
      if (!name) {
        return;
      }
      if (windows.has(name)) {
        context.addIssue({
          code: "custom",
          path: ["windows", index, "name"],
          message: "窗口名称重复。",
        });
      }
      windows.add(name);
    });
  });

type AccountFormValues = z.infer<typeof accountSchema>;

type WindowFormValues = AccountFormValues["windows"][number];

type AccountDialogProps = {
  provider: AiProviderItem;
  account?: AiProviderAccountItem;
  onClose: () => void;
};

export function AccountDialog({ provider, account, onClose }: AccountDialogProps) {
  const queryClient = useQueryClient();
  const accountQuery = useQuery({
    queryKey: ["ai-provider-account", account?.metadata.name],
    queryFn: () => fetchProviderAccount(account?.metadata.name ?? ""),
    initialData: account,
    enabled: Boolean(account),
    retry: false,
  });
  const currentAccount = accountQuery.data ?? account;
  const form = useForm<AccountFormValues>({
    resolver: zodResolver(accountSchema) as Resolver<AccountFormValues>,
    defaultValues: initialValues(account),
  });
  const windows = useFieldArray({ control: form.control, name: "windows" });
  const modelConcurrency = useFieldArray({
    control: form.control,
    name: "concurrencyByModel",
  });
  const accountType = useWatch({ control: form.control, name: "accountType" });
  const enabled = useWatch({ control: form.control, name: "enabled" });
  const freePlan = useWatch({ control: form.control, name: "freePlan" });
  const creditsEnabled = useWatch({ control: form.control, name: "creditsEnabled" });

  const mutation = useMutation({
    mutationFn: (values: AccountFormValues) => {
      const windowsValue = toWindows(values.windows);
      const concurrencyRecord = toConcurrencyRecord(values.concurrencyByModel);
      if (account && currentAccount) {
        const operations: JsonPatchOperation[] = [
          { op: "add", path: "/spec/displayName", value: values.displayName },
          { op: "add", path: "/spec/accountType", value: values.accountType },
          { op: "add", path: "/spec/weight", value: values.weight },
          { op: "add", path: "/spec/priority", value: values.priority },
          { op: "add", path: "/spec/maxConcurrency", value: values.maxConcurrency },
          { op: "add", path: "/spec/maxRPM", value: values.maxRPM },
          { op: "add", path: "/spec/enabled", value: values.enabled },
          { op: "add", path: "/spec/freePlan", value: values.freePlan },
          { op: "add", path: "/spec/creditsEnabled", value: values.creditsEnabled },
          ...optionalStringOperation("/spec/keyName", values.keyName, currentAccount.spec.keyName),
          ...optionalStringOperation("/spec/keyPlan", values.keyPlan, currentAccount.spec.keyPlan),
          ...optionalNumberOperation(
            "/spec/spendCapPoints",
            parseNumber(values.spendCapPoints),
            currentAccount.spec.spendCapPoints,
          ),
          ...arrayOperation(
            "/spec/windows",
            windowsValue.length > 0 ? windowsValue : undefined,
            currentAccount.spec.windows,
          ),
          ...arrayOperation(
            "/spec/concurrencyByModel",
            Object.keys(concurrencyRecord).length > 0 ? concurrencyRecord : undefined,
            currentAccount.spec.concurrencyByModel,
          ),
          ...balanceProbeOperations(currentAccount.spec.balanceProbe, values),
        ];
        if (values.apiKey) {
          operations.push({ op: "add", path: "/spec/apiKey", value: values.apiKey });
        }
        return patchProviderAccount(currentAccount.metadata.name, operations);
      }
      const balanceProbe = buildBalanceProbe(values);
      const spec: AiProviderAccountInput["spec"] = {
        provider: provider.metadata.name,
        displayName: values.displayName || undefined,
        accountType: values.accountType,
        apiKey: values.apiKey || undefined,
        keyName: values.keyName || undefined,
        keyPlan: values.keyPlan || undefined,
        spendCapPoints: parseNumber(values.spendCapPoints),
        weight: values.weight,
        priority: values.priority,
        maxConcurrency: values.maxConcurrency,
        maxRPM: values.maxRPM,
        enabled: values.enabled,
        freePlan: values.freePlan,
        creditsEnabled: values.creditsEnabled,
        windows: windowsValue.length > 0 ? windowsValue : undefined,
        concurrencyByModel:
          Object.keys(concurrencyRecord).length > 0 ? concurrencyRecord : undefined,
        balanceProbe,
      };
      return createProviderAccount({
        apiVersion: AI_API_VERSION,
        kind: AI_PROVIDER_ACCOUNT_KIND,
        metadata: { name: values.name },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-accounts"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-providers"] });
      toast.success(account ? "供应商账号已保存。" : "供应商账号已创建。");
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
      icon={<KeyRound />}
      title={account ? "编辑账号" : "添加账号"}
      description="账号用于调用供应商接口，可配置密钥标识、并发与额度窗口。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      submitLabel={account ? "保存账号" : "创建账号"}
      contentClassName="sm:max-w-3xl"
    >
      <FormDialogSection title="基础信息">
        <div className="space-y-3">
          <FormField
            orientation="horizontal"
            label="名称"
            htmlFor="ai-account-name"
            hint={account ? "名称创建后不可修改。" : RESOURCE_NAME_HINT}
            error={form.formState.errors.name?.message}
          >
            <Input id="ai-account-name" disabled={Boolean(account)} {...form.register("name")} />
          </FormField>
          <FormField
            orientation="horizontal"
            label="显示名称"
            htmlFor="ai-account-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input id="ai-account-display-name" {...form.register("displayName")} />
          </FormField>
          <FormField
            orientation="horizontal"
            label="账号类型"
            htmlFor="ai-account-type"
            error={form.formState.errors.accountType?.message}
          >
            <Select
              value={accountType}
              onValueChange={(value) =>
                form.setValue("accountType", value as AiAccountType, { shouldDirty: true })
              }
            >
              <SelectTrigger id="ai-account-type" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ACCOUNT_TYPE_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
          <FormField
            orientation="horizontal"
            label="API Key"
            htmlFor="ai-account-api-key"
            hint={account ? "留空表示保留现有密钥。" : "OAuth 或自定义账号可留空。"}
            error={form.formState.errors.apiKey?.message}
          >
            <Input
              id="ai-account-api-key"
              type="password"
              autoComplete="new-password"
              {...form.register("apiKey")}
            />
          </FormField>
          <FormField
            orientation="horizontal"
            label="密钥名称"
            htmlFor="ai-account-key-name"
            hint="用于在多个密钥之间区分来源，例如主账号或备用账号。"
            error={form.formState.errors.keyName?.message}
          >
            <Input id="ai-account-key-name" {...form.register("keyName")} />
          </FormField>
          <FormField
            orientation="horizontal"
            label="套餐"
            htmlFor="ai-account-key-plan"
            hint="记录密钥所属套餐，例如免费版或团队版。"
            error={form.formState.errors.keyPlan?.message}
          >
            <Input id="ai-account-key-plan" {...form.register("keyPlan")} />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="计费">
        <div className="space-y-3">
          <FormField
            orientation="horizontal"
            label="Token 上限"
            htmlFor="ai-account-spend-cap"
            hint="留空或 0 表示不限制该账号的标准 Token 消耗。"
            error={form.formState.errors.spendCapPoints?.message}
          >
            <Input
              id="ai-account-spend-cap"
              type="number"
              min={0}
              {...form.register("spendCapPoints")}
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="调度">
        <div className="space-y-3">
          <FormField
            orientation="horizontal"
            label="权重"
            htmlFor="ai-account-weight"
            error={form.formState.errors.weight?.message}
          >
            <Input id="ai-account-weight" type="number" min={1} {...form.register("weight")} />
          </FormField>
          <FormField
            orientation="horizontal"
            label="优先级"
            htmlFor="ai-account-priority"
            error={form.formState.errors.priority?.message}
          >
            <Input id="ai-account-priority" type="number" min={0} {...form.register("priority")} />
          </FormField>
          <FormField
            orientation="horizontal"
            label="最大并发"
            htmlFor="ai-account-max-concurrency"
            error={form.formState.errors.maxConcurrency?.message}
          >
            <Input
              id="ai-account-max-concurrency"
              type="number"
              min={1}
              {...form.register("maxConcurrency")}
            />
          </FormField>
          <FormField
            orientation="horizontal"
            label="每分钟请求上限"
            htmlFor="ai-account-max-rpm"
            hint="0 表示不限制。"
            error={form.formState.errors.maxRPM?.message}
          >
            <Input id="ai-account-max-rpm" type="number" min={0} {...form.register("maxRPM")} />
          </FormField>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <SwitchRow
            label="启用账号"
            checked={enabled}
            onChange={(checked) => form.setValue("enabled", checked, { shouldDirty: true })}
          />
          <SwitchRow
            label="免费额度"
            checked={freePlan}
            onChange={(checked) => form.setValue("freePlan", checked, { shouldDirty: true })}
          />
          <SwitchRow
            label="点数计费"
            checked={creditsEnabled}
            onChange={(checked) => form.setValue("creditsEnabled", checked, { shouldDirty: true })}
          />
        </div>
      </FormDialogSection>

      <FormDialogSection
        title={
          <span className="flex items-center justify-between gap-2">
            额度窗口
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() =>
                windows.append({
                  name: "",
                  unit: "TOKEN",
                  limit: "",
                  used: "",
                  resetsAt: "",
                })
              }
            >
              <Plus />
              添加窗口
            </Button>
          </span>
        }
      >
        <p className="text-xs text-muted-foreground">
          按时间窗口跟踪用量，例如 5 小时、每日或每月；窗口名称为空时显示为“窗口”。
        </p>
        {windows.fields.length === 0 ? (
          <p className="border border-dashed p-4 text-sm text-muted-foreground">
            未设置额度窗口，账号只使用上方配额与余额。
          </p>
        ) : (
          <div className="space-y-3">
            {windows.fields.map((field, index) => (
              <WindowRow
                key={field.id}
                form={form}
                index={index}
                onRemove={() => windows.remove(index)}
              />
            ))}
          </div>
        )}
      </FormDialogSection>

      <FormDialogSection
        title={
          <span className="flex items-center justify-between gap-2">
            模型并发覆盖
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => modelConcurrency.append({ model: "", value: "" })}
            >
              <Plus />
              添加模型
            </Button>
          </span>
        }
      >
        <p className="text-xs text-muted-foreground">
          为单个模型设置独立并发上限；未列出的模型使用账号最大并发。
        </p>
        {modelConcurrency.fields.length === 0 ? (
          <p className="border border-dashed p-4 text-sm text-muted-foreground">
            未设置模型级并发覆盖。
          </p>
        ) : (
          <div className="space-y-2">
            {modelConcurrency.fields.map((field, index) => (
              <div key={field.id} className="space-y-1">
                <div className="flex items-start gap-2">
                  <Input
                    placeholder="模型编码，如 gpt-4o-mini"
                    aria-label={`模型 ${index + 1} 编码`}
                    aria-invalid={Boolean(form.formState.errors.concurrencyByModel?.[index]?.model)}
                    {...form.register(`concurrencyByModel.${index}.model`)}
                  />
                  <Input
                    type="number"
                    min={0}
                    placeholder="并发"
                    className="w-28"
                    aria-label={`模型 ${index + 1} 并发`}
                    aria-invalid={Boolean(form.formState.errors.concurrencyByModel?.[index]?.value)}
                    {...form.register(`concurrencyByModel.${index}.value`)}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`删除模型 ${index + 1}`}
                    onClick={() => modelConcurrency.remove(index)}
                  >
                    <Trash2 />
                  </Button>
                </div>
                {form.formState.errors.concurrencyByModel?.[index]?.model?.message ? (
                  <p className="ps-1 text-xs text-destructive">
                    {form.formState.errors.concurrencyByModel[index]?.model?.message}
                  </p>
                ) : null}
                {form.formState.errors.concurrencyByModel?.[index]?.value?.message ? (
                  <p className="ps-1 text-xs text-destructive">
                    {form.formState.errors.concurrencyByModel[index]?.value?.message}
                  </p>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </FormDialogSection>

      <FormDialogSection title="余额查询">
        <p className="text-xs text-muted-foreground">
          供应商余额接口与默认实现不同时，可在此指定自定义查询地址与请求头。
        </p>
        <div className="space-y-3">
          <FormField
            orientation="horizontal"
            label="地址"
            htmlFor="ai-account-probe-url"
            hint="完整地址；留空使用供应商默认接口。"
            error={form.formState.errors.balanceProbeUrl?.message}
          >
            <Input
              id="ai-account-probe-url"
              placeholder="https://api.example.com/user/balance"
              {...form.register("balanceProbeUrl")}
            />
          </FormField>
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField
              orientation="vertical"
              label="路径"
              htmlFor="ai-account-probe-path"
              error={form.formState.errors.balanceProbePath?.message}
            >
              <Input
                id="ai-account-probe-path"
                placeholder="/balance"
                {...form.register("balanceProbePath")}
              />
            </FormField>
            <FormField
              orientation="vertical"
              label="方法"
              htmlFor="ai-account-probe-method"
              error={form.formState.errors.balanceProbeMethod?.message}
            >
              <Input
                id="ai-account-probe-method"
                placeholder="GET"
                {...form.register("balanceProbeMethod")}
              />
            </FormField>
          </div>
          <FormField
            orientation="horizontal"
            label="请求头"
            htmlFor="ai-account-probe-header"
            hint="携带令牌的请求头名称，留空使用默认行为。"
            error={form.formState.errors.balanceProbeHeaderName?.message}
          >
            <Input
              id="ai-account-probe-header"
              placeholder="Authorization"
              {...form.register("balanceProbeHeaderName")}
            />
          </FormField>
          <FormField
            orientation="horizontal"
            label="令牌"
            htmlFor="ai-account-probe-token"
            hint={account?.spec.balanceProbe ? "留空表示保留现有令牌。" : "可选。"}
            error={form.formState.errors.balanceProbeToken?.message}
          >
            <Input
              id="ai-account-probe-token"
              type="password"
              autoComplete="new-password"
              {...form.register("balanceProbeToken")}
            />
          </FormField>
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}

type SwitchRowProps = {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
};

function SwitchRow({ label, checked, onChange }: SwitchRowProps) {
  return (
    <div className="flex items-center justify-between gap-3 border p-3">
      <p className="text-sm font-medium">{label}</p>
      <Switch checked={checked} onCheckedChange={onChange} aria-label={label} />
    </div>
  );
}

type WindowRowProps = {
  form: UseFormReturn<AccountFormValues>;
  index: number;
  onRemove: () => void;
};

function WindowRow({ form, index, onRemove }: WindowRowProps) {
  const unit = useWatch({ control: form.control, name: `windows.${index}.unit` });
  return (
    <div className="space-y-2 border p-3">
      <div className="flex items-center gap-2">
        <Input
          placeholder="窗口名称，如 5h / daily"
          aria-label={`窗口 ${index + 1} 名称`}
          aria-invalid={Boolean(form.formState.errors.windows?.[index]?.name)}
          {...form.register(`windows.${index}.name`)}
        />
        <Select
          value={unit}
          onValueChange={(value) =>
            form.setValue(`windows.${index}.unit`, value as AiQuotaUnit, {
              shouldDirty: true,
            })
          }
        >
          <SelectTrigger className="w-28" aria-label={`窗口 ${index + 1} 单位`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {QUOTA_UNIT_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={`删除窗口 ${index + 1}`}
          onClick={onRemove}
        >
          <Trash2 />
        </Button>
      </div>
      <div className="grid gap-2 sm:grid-cols-3">
        <WindowField label="上限" error={form.formState.errors.windows?.[index]?.limit?.message}>
          <Input
            type="number"
            min={0}
            placeholder="不限"
            aria-label={`窗口 ${index + 1} 上限`}
            {...form.register(`windows.${index}.limit`)}
          />
        </WindowField>
        <WindowField label="已用" error={form.formState.errors.windows?.[index]?.used?.message}>
          <Input
            type="number"
            min={0}
            placeholder="0"
            aria-label={`窗口 ${index + 1} 已用`}
            {...form.register(`windows.${index}.used`)}
          />
        </WindowField>
        <WindowField
          label="重置时间"
          error={form.formState.errors.windows?.[index]?.resetsAt?.message}
        >
          <Input
            type="datetime-local"
            aria-label={`窗口 ${index + 1} 重置时间`}
            {...form.register(`windows.${index}.resetsAt`)}
          />
        </WindowField>
      </div>
      {form.formState.errors.windows?.[index]?.name?.message ? (
        <p className="text-xs text-destructive">
          {form.formState.errors.windows[index]?.name?.message}
        </p>
      ) : null}
    </div>
  );
}

type WindowFieldProps = {
  label: string;
  error?: string;
  children: ReactNode;
};

function WindowField({ label, error, children }: WindowFieldProps) {
  return (
    <label className="space-y-1 text-sm">
      <span className="text-xs text-muted-foreground">{label}</span>
      {children}
      {error ? <span className="block text-xs text-destructive">{error}</span> : null}
    </label>
  );
}

function initialValues(account: AiProviderAccountItem | undefined): AccountFormValues {
  const spec = account?.spec;
  return {
    name: account?.metadata.name ?? "",
    displayName: spec?.displayName ?? "",
    accountType: spec?.accountType ?? DEFAULT_ACCOUNT_SPEC.accountType,
    apiKey: "",
    keyName: spec?.keyName ?? "",
    keyPlan: spec?.keyPlan ?? "",
    spendCapPoints: spec?.spendCapPoints == null ? "" : String(spec.spendCapPoints),
    weight: spec?.weight ?? DEFAULT_ACCOUNT_SPEC.weight,
    priority: spec?.priority ?? DEFAULT_ACCOUNT_SPEC.priority,
    maxConcurrency: spec?.maxConcurrency ?? DEFAULT_ACCOUNT_SPEC.maxConcurrency,
    maxRPM: spec?.maxRPM ?? DEFAULT_ACCOUNT_SPEC.maxRPM,
    enabled: spec?.enabled ?? DEFAULT_ACCOUNT_SPEC.enabled,
    freePlan: spec?.freePlan ?? DEFAULT_ACCOUNT_SPEC.freePlan,
    creditsEnabled: spec?.creditsEnabled ?? DEFAULT_ACCOUNT_SPEC.creditsEnabled,
    windows: (spec?.windows ?? []).map((window) => ({
      name: window.name ?? "",
      unit: window.unit ?? "TOKEN",
      limit: window.limit == null ? "" : String(window.limit),
      used: window.used == null ? "" : String(window.used),
      resetsAt: beijingDateTimeInputValue(window.resetsAt),
    })),
    concurrencyByModel: Object.entries(spec?.concurrencyByModel ?? {}).map(([model, value]) => ({
      model,
      value: String(value),
    })),
    balanceProbeUrl: spec?.balanceProbe?.url ?? "",
    balanceProbePath: spec?.balanceProbe?.path ?? "",
    balanceProbeMethod: spec?.balanceProbe?.method ?? "",
    balanceProbeToken: "",
    balanceProbeHeaderName: spec?.balanceProbe?.headerName ?? "",
  };
}

function toWindows(
  rows: readonly WindowFormValues[],
): NonNullable<AiProviderAccountItem["spec"]["windows"]> {
  return rows
    .map((row) => ({
      name: row.name.trim() || undefined,
      unit: row.unit,
      limit: parseNumber(row.limit),
      used: parseNumber(row.used) ?? 0,
      resetsAt: row.resetsAt ? beijingDateTimeInputToIso(row.resetsAt) : undefined,
    }))
    .filter(
      (window) =>
        window.name !== undefined ||
        window.limit !== undefined ||
        window.used > 0 ||
        window.resetsAt !== undefined,
    );
}

function toConcurrencyRecord(
  rows: readonly AccountFormValues["concurrencyByModel"][number][],
): Record<string, number> {
  const record: Record<string, number> = {};
  for (const row of rows) {
    const model = row.model.trim();
    const value = parseNumber(row.value);
    if (model && value !== undefined) {
      record[model] = Math.trunc(value);
    }
  }
  return record;
}

function buildBalanceProbe(values: AccountFormValues): AiProviderAccountBalanceProbe | undefined {
  const probe: AiProviderAccountBalanceProbe = {
    url: values.balanceProbeUrl || undefined,
    path: values.balanceProbePath || undefined,
    method: values.balanceProbeMethod || undefined,
    token: values.balanceProbeToken || undefined,
    headerName: values.balanceProbeHeaderName || undefined,
  };
  return Object.values(probe).some(Boolean) ? probe : undefined;
}

function balanceProbeOperations(
  existing: AiProviderAccountBalanceProbe | undefined,
  values: AccountFormValues,
): JsonPatchOperation[] {
  const probe = buildBalanceProbe(values);
  if (!probe && !existing) {
    return [];
  }
  if (!existing) {
    return probe ? [{ op: "add", path: "/spec/balanceProbe", value: probe }] : [];
  }
  const operations: JsonPatchOperation[] = [
    { op: "add", path: "/spec/balanceProbe/url", value: probe?.url ?? "" },
    { op: "add", path: "/spec/balanceProbe/path", value: probe?.path ?? "" },
    { op: "add", path: "/spec/balanceProbe/method", value: probe?.method ?? "" },
    { op: "add", path: "/spec/balanceProbe/headerName", value: probe?.headerName ?? "" },
  ];
  if (probe?.token) {
    operations.push({ op: "add", path: "/spec/balanceProbe/token", value: probe.token });
  }
  return operations;
}

function optionalStringOperation(
  path: string,
  value: string,
  existing: string | undefined,
): JsonPatchOperation[] {
  const trimmed = value.trim();
  if (trimmed) {
    return [{ op: "add", path, value: trimmed }];
  }
  return existing ? [{ op: "remove", path }] : [];
}

function optionalNumberOperation(
  path: string,
  value: number | undefined,
  existing: number | undefined,
): JsonPatchOperation[] {
  if (value !== undefined) {
    return [{ op: "add", path, value }];
  }
  return existing !== undefined ? [{ op: "remove", path }] : [];
}

function arrayOperation(path: string, value: unknown, existing: unknown): JsonPatchOperation[] {
  if (value !== undefined) {
    return [{ op: "add", path, value }];
  }
  return existing !== undefined ? [{ op: "remove", path }] : [];
}

function parseNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}
