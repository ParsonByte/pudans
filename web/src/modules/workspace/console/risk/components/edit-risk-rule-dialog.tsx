import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
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
  type RiskRuleItem,
  resolveRiskApiError,
  riskRuleDisplayName,
  updateRiskRule,
} from "../lib/api";

type EditRiskRuleDialogProps = {
  rule: RiskRuleItem;
  onClose: () => void;
};

const editRiskRuleSchema = z.object({
  description: z.string().trim().max(200, "描述不能超过 200 个字符。"),
  enabled: z.boolean(),
  windowSeconds: z.coerce
    .number()
    .int()
    .min(1, "窗口至少 1 秒。")
    .max(86400, "窗口不能超过 86400 秒。"),
  threshold: z.coerce.number().int().min(1, "阈值至少为 1。").max(100000, "阈值不能超过 100000。"),
  blockSeconds: z.coerce
    .number()
    .int()
    .min(0, "封禁时长不能为负数。")
    .max(86400, "封禁时长不能超过 86400 秒。"),
  action: z.enum(["BLOCK", "ALERT"]),
});

type EditRiskRuleValues = z.infer<typeof editRiskRuleSchema>;

export function EditRiskRuleDialog({ rule, onClose }: EditRiskRuleDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<EditRiskRuleValues>({
    resolver: zodResolver(editRiskRuleSchema) as Resolver<EditRiskRuleValues>,
    defaultValues: {
      description: rule.spec.description ?? "",
      enabled: rule.spec.enabled ?? true,
      windowSeconds: rule.spec.windowSeconds ?? 60,
      threshold: rule.spec.threshold ?? 60,
      blockSeconds: rule.spec.blockSeconds ?? 60,
      action: rule.spec.action === "ALERT" ? "ALERT" : "BLOCK",
    },
  });
  const action = useWatch({ control: form.control, name: "action" });

  const mutation = useMutation({
    mutationFn: (values: EditRiskRuleValues) =>
      updateRiskRule({
        ...rule,
        spec: {
          ...rule.spec,
          description: values.description || undefined,
          enabled: values.enabled,
          windowSeconds: values.windowSeconds,
          threshold: values.threshold,
          blockSeconds: values.action === "ALERT" ? 0 : values.blockSeconds,
          action: values.action,
        },
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["risk-rules"] });
      await queryClient.invalidateQueries({ queryKey: ["risk-events"] });
      toast.success("风险规则已保存。");
      onClose();
    },
    onError: (error) => toast.error(resolveRiskApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<ShieldAlert />}
      title={`编辑风险规则：${riskRuleDisplayName(rule)}`}
      description="调整该风险场景的触发窗口、阈值和处置动作。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.55}
      submitLabel="保存规则"
    >
      <div className="flex items-center justify-between gap-4 border p-4">
        <div className="space-y-1">
          <p className="text-sm font-medium">启用规则</p>
          <p className="text-xs text-muted-foreground">关闭后该场景不再参与风险判定。</p>
        </div>
        <Controller
          control={form.control}
          name="enabled"
          render={({ field }) => (
            <Switch
              checked={field.value}
              onCheckedChange={field.onChange}
              aria-label="启用风险规则"
            />
          )}
        />
      </div>

      <FormField
        label="描述"
        htmlFor="risk-rule-description"
        error={form.formState.errors.description?.message}
      >
        <Textarea
          id="risk-rule-description"
          rows={3}
          placeholder="说明该规则的触发条件和处置方式。"
          {...form.register("description")}
        />
      </FormField>

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="统计窗口（秒）"
          htmlFor="risk-rule-window"
          error={form.formState.errors.windowSeconds?.message}
        >
          <Input id="risk-rule-window" type="number" min={1} {...form.register("windowSeconds")} />
        </FormField>
        <FormField
          label="触发阈值"
          htmlFor="risk-rule-threshold"
          error={form.formState.errors.threshold?.message}
        >
          <Input id="risk-rule-threshold" type="number" min={1} {...form.register("threshold")} />
        </FormField>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="处置动作"
          htmlFor="risk-rule-action"
          error={form.formState.errors.action?.message}
        >
          <Controller
            control={form.control}
            name="action"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="risk-rule-action" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="BLOCK">拦截</SelectItem>
                  <SelectItem value="ALERT">仅告警</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
        <FormField
          label="封禁时长（秒）"
          htmlFor="risk-rule-block"
          error={form.formState.errors.blockSeconds?.message}
          hint={action === "ALERT" ? "仅告警模式不执行封禁。" : undefined}
        >
          <Input
            id="risk-rule-block"
            type="number"
            min={0}
            disabled={action === "ALERT"}
            {...form.register("blockSeconds")}
          />
        </FormField>
      </div>
    </FormDialog>
  );
}
