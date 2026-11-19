import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { KeyRound } from "lucide-react";
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
  AI_PROVIDER_ACCOUNT_KIND,
  type AiAccountType,
  type AiProviderAccountItem,
  type AiProviderItem,
  type JsonPatchOperation,
  createProviderAccount,
  patchProviderAccount,
  resolveAiProviderApiError,
} from "../lib/api";
import {
  ACCOUNT_TYPE_OPTIONS,
  DEFAULT_ACCOUNT_SPEC,
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "../lib/model";
import {
  ProviderDialogSection,
  ProviderDialogShell,
  ProviderFieldRow,
} from "./provider-dialog-shell";

const ACCOUNT_TYPE_VALUES = ACCOUNT_TYPE_OPTIONS.map((option) => option.value) as [
  AiAccountType,
  ...AiAccountType[],
];

const accountSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入名称。")
    .max(63, "名称不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  displayName: z.string().trim().max(100, "显示名称不能超过 100 个字符。"),
  accountType: z.enum(ACCOUNT_TYPE_VALUES),
  apiKey: z.string().trim().max(1000, "密钥过长。"),
  weight: z.coerce.number().int().min(1, "权重至少为 1。").max(1000, "权重不能超过 1000。"),
  priority: z.coerce.number().int().min(0, "优先级不能为负数。").max(1000),
  maxConcurrency: z.coerce.number().int().min(1, "并发数至少为 1。").max(100000),
  maxRPM: z.coerce.number().int().min(0, "每分钟请求数不能为负数。").max(1000000),
  enabled: z.boolean(),
  freePlan: z.boolean(),
  creditsEnabled: z.boolean(),
});

type AccountFormValues = z.infer<typeof accountSchema>;

type AccountDialogProps = {
  provider: AiProviderItem;
  account?: AiProviderAccountItem;
  onClose: () => void;
};

export function AccountDialog({ provider, account, onClose }: AccountDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<AccountFormValues>({
    resolver: zodResolver(accountSchema) as Resolver<AccountFormValues>,
    defaultValues: initialValues(account),
  });
  const accountType = useWatch({ control: form.control, name: "accountType" });
  const enabled = useWatch({ control: form.control, name: "enabled" });
  const freePlan = useWatch({ control: form.control, name: "freePlan" });
  const creditsEnabled = useWatch({ control: form.control, name: "creditsEnabled" });

  const mutation = useMutation({
    mutationFn: (values: AccountFormValues) => {
      if (account) {
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
        ];
        if (values.apiKey) {
          operations.push({ op: "add", path: "/spec/apiKey", value: values.apiKey });
        }
        return patchProviderAccount(account.metadata.name, operations);
      }
      return createProviderAccount({
        apiVersion: AI_API_VERSION,
        kind: AI_PROVIDER_ACCOUNT_KIND,
        metadata: { name: values.name },
        spec: {
          provider: provider.metadata.name,
          displayName: values.displayName || undefined,
          accountType: values.accountType,
          apiKey: values.apiKey || undefined,
          weight: values.weight,
          priority: values.priority,
          maxConcurrency: values.maxConcurrency,
          maxRPM: values.maxRPM,
          enabled: values.enabled,
          freePlan: values.freePlan,
          creditsEnabled: values.creditsEnabled,
        },
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
    <ProviderDialogShell
      onClose={onClose}
      icon={<KeyRound />}
      title={account ? "编辑账号" : "添加账号"}
      description="账号用于调用供应商接口，可配置权重、优先级和并发限制。"
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      submitLabel={account ? "保存账号" : "创建账号"}
    >
      <ProviderDialogSection title="基础信息">
        <div className="space-y-3">
          <ProviderFieldRow
            label="名称"
            htmlFor="ai-account-name"
            hint={account ? "名称创建后不可修改。" : RESOURCE_NAME_HINT}
            error={form.formState.errors.name?.message}
          >
            <Input id="ai-account-name" disabled={Boolean(account)} {...form.register("name")} />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="显示名称"
            htmlFor="ai-account-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input id="ai-account-display-name" {...form.register("displayName")} />
          </ProviderFieldRow>
          <ProviderFieldRow
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
          </ProviderFieldRow>
          <ProviderFieldRow
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
          </ProviderFieldRow>
        </div>
      </ProviderDialogSection>

      <ProviderDialogSection title="调度">
        <div className="space-y-3">
          <ProviderFieldRow
            label="权重"
            htmlFor="ai-account-weight"
            error={form.formState.errors.weight?.message}
          >
            <Input id="ai-account-weight" type="number" min={1} {...form.register("weight")} />
          </ProviderFieldRow>
          <ProviderFieldRow
            label="优先级"
            htmlFor="ai-account-priority"
            error={form.formState.errors.priority?.message}
          >
            <Input id="ai-account-priority" type="number" min={0} {...form.register("priority")} />
          </ProviderFieldRow>
          <ProviderFieldRow
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
          </ProviderFieldRow>
          <ProviderFieldRow
            label="每分钟请求上限"
            htmlFor="ai-account-max-rpm"
            hint="0 表示不限制。"
            error={form.formState.errors.maxRPM?.message}
          >
            <Input id="ai-account-max-rpm" type="number" min={0} {...form.register("maxRPM")} />
          </ProviderFieldRow>
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

function initialValues(account: AiProviderAccountItem | undefined): AccountFormValues {
  const spec = account?.spec;
  return {
    name: account?.metadata.name ?? "",
    displayName: spec?.displayName ?? "",
    accountType: spec?.accountType ?? DEFAULT_ACCOUNT_SPEC.accountType,
    apiKey: "",
    weight: spec?.weight ?? DEFAULT_ACCOUNT_SPEC.weight,
    priority: spec?.priority ?? DEFAULT_ACCOUNT_SPEC.priority,
    maxConcurrency: spec?.maxConcurrency ?? DEFAULT_ACCOUNT_SPEC.maxConcurrency,
    maxRPM: spec?.maxRPM ?? DEFAULT_ACCOUNT_SPEC.maxRPM,
    enabled: spec?.enabled ?? DEFAULT_ACCOUNT_SPEC.enabled,
    freePlan: spec?.freePlan ?? DEFAULT_ACCOUNT_SPEC.freePlan,
    creditsEnabled: spec?.creditsEnabled ?? DEFAULT_ACCOUNT_SPEC.creditsEnabled,
  };
}
