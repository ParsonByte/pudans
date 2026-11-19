import { useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Gauge } from "lucide-react";
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

import {
  type AiProviderAccountItem,
  type AiQuotaUnit,
  resolveAiProviderApiError,
  updateAccountQuota,
} from "../lib/api";
import { QUOTA_UNIT_OPTIONS, formatQuota } from "../lib/model";

const quotaSchema = z.object({
  quotaUnit: z.string(),
  quotaLimit: z
    .string()
    .trim()
    .refine((value) => value === "" || /^\d+(\.\d+)?$/.test(value), "请输入非负数字。"),
  balanceUnit: z.string(),
  balanceLeft: z
    .string()
    .trim()
    .refine((value) => value === "" || /^\d+(\.\d+)?$/.test(value), "请输入非负数字。"),
});

type QuotaFormValues = z.infer<typeof quotaSchema>;

type QuotaDialogProps = {
  account: AiProviderAccountItem;
  onClose: () => void;
};

export function QuotaDialog({ account, onClose }: QuotaDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<QuotaFormValues>({
    resolver: zodResolver(quotaSchema),
    defaultValues: {
      quotaUnit: account.spec.quota?.unit ?? "TOKEN",
      quotaLimit: account.spec.quota?.limit == null ? "" : String(account.spec.quota.limit),
      balanceUnit: account.spec.balance?.unit ?? "USD",
      balanceLeft: account.spec.balance?.left == null ? "" : String(account.spec.balance.left),
    },
  });
  const quotaUnit = useWatch({ control: form.control, name: "quotaUnit" });
  const balanceUnit = useWatch({ control: form.control, name: "balanceUnit" });

  const mutation = useMutation({
    mutationFn: (values: QuotaFormValues) =>
      updateAccountQuota(
        account.metadata.name,
        {
          unit: values.quotaUnit as AiQuotaUnit,
          limit: values.quotaLimit === "" ? undefined : Number(values.quotaLimit),
          used: account.spec.quota?.used,
          resetAt: account.spec.quota?.resetAt,
        },
        {
          unit: values.balanceUnit as AiQuotaUnit,
          left: values.balanceLeft === "" ? undefined : Number(values.balanceLeft),
        },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-accounts"] });
      toast.success("账号配额已更新。");
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
      icon={<Gauge />}
      title="调整配额与余额"
      description={`当前用量：${formatQuota(account.spec.quota)}。配额上限留空表示不限制。`}
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      submitLabel="保存配额"
      maxHeightRatio={0.5}
    >
      <FormDialogSection title="配额">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="计量单位"
            htmlFor="ai-quota-unit"
            error={form.formState.errors.quotaUnit?.message}
          >
            <Select
              value={quotaUnit}
              onValueChange={(value) => form.setValue("quotaUnit", value, { shouldDirty: true })}
            >
              <SelectTrigger id="ai-quota-unit" className="w-full">
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
          </FormField>
          <FormField
            label="配额上限"
            htmlFor="ai-quota-limit"
            hint="留空表示不限制。"
            error={form.formState.errors.quotaLimit?.message}
          >
            <Input id="ai-quota-limit" type="number" min={0} {...form.register("quotaLimit")} />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="余额">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="余额单位"
            htmlFor="ai-balance-unit"
            error={form.formState.errors.balanceUnit?.message}
          >
            <Select
              value={balanceUnit}
              onValueChange={(value) => form.setValue("balanceUnit", value, { shouldDirty: true })}
            >
              <SelectTrigger id="ai-balance-unit" className="w-full">
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
          </FormField>
          <FormField
            label="剩余余额"
            htmlFor="ai-balance-left"
            error={form.formState.errors.balanceLeft?.message}
          >
            <Input id="ai-balance-left" type="number" min={0} {...form.register("balanceLeft")} />
          </FormField>
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}
