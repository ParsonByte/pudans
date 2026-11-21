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

import { createRiskRule, resolveRiskApiError } from "../lib/api";

type CreateRiskRuleDialogProps = {
  onClose: () => void;
};

const NAME_PATTERN = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/;

const createRiskRuleSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入规则名称。")
    .max(63, "规则名称不能超过 63 个字符。")
    .regex(NAME_PATTERN, "仅支持小写字母、数字和中划线，且以字母或数字开头和结尾。"),
  scene: z.string().trim().min(1, "请输入风险场景。").max(80, "场景不能超过 80 个字符。"),
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

type CreateRiskRuleValues = z.infer<typeof createRiskRuleSchema>;

export function CreateRiskRuleDialog({ onClose }: CreateRiskRuleDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<CreateRiskRuleValues>({
    resolver: zodResolver(createRiskRuleSchema) as Resolver<CreateRiskRuleValues>,
    defaultValues: {
      name: "",
      scene: "",
      description: "",
      enabled: true,
      windowSeconds: 60,
      threshold: 60,
      blockSeconds: 60,
      action: "BLOCK",
    },
  });
  const action = useWatch({ control: form.control, name: "action" });

  const mutation = useMutation({
    mutationFn: (values: CreateRiskRuleValues) => createRiskRule(values),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["risk-rules"] });
      await queryClient.invalidateQueries({ queryKey: ["risk-events"] });
      toast.success("风险规则已创建。");
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
      title="新建风险规则"
      description="定义风险场景、统计窗口、阈值和处置动作。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.6}
      submitLabel="创建规则"
    >
      <div className="flex items-center justify-between gap-4 border p-4">
        <div className="space-y-1">
          <p className="text-sm font-medium">启用规则</p>
          <p className="text-xs text-muted-foreground">创建后立即参与风险判定。</p>
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

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="规则名称"
          htmlFor="risk-rule-name"
          error={form.formState.errors.name?.message}
        >
          <Input
            id="risk-rule-name"
            placeholder="例如 login-burst"
            autoComplete="off"
            spellCheck={false}
            {...form.register("name")}
          />
        </FormField>
        <FormField
          label="风险场景"
          htmlFor="risk-rule-scene"
          error={form.formState.errors.scene?.message}
        >
          <Input
            id="risk-rule-scene"
            placeholder="例如 login-burst"
            autoComplete="off"
            spellCheck={false}
            {...form.register("scene")}
          />
        </FormField>
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
