import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { KeyRound } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

import { AgentsField, EnabledField } from "../../components/library-dialog-fields";
import { resolveLibraryApiError } from "../../lib/api";
import { resourceGenerateName } from "../../lib/model";
import {
  AI_API_VERSION,
  AI_ENVIRONMENT_VARIABLE_KIND,
  ENVIRONMENT_VARIABLES_QUERY_KEY,
  ENVIRONMENT_VARIABLE_NAME_PATTERN,
  type EnvironmentVariableItem,
  type EnvironmentVariableSpec,
  clearEnvironmentVariableValue,
  createEnvironmentVariable,
  updateEnvironmentVariable,
  updateEnvironmentVariableValue,
} from "../lib/api";

const variableSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "请输入变量名称。")
      .max(100, "名称不能超过 100 个字符。")
      .refine(
        (value) => ENVIRONMENT_VARIABLE_NAME_PATTERN.test(value),
        "名称必须由大写字母、数字和下划线组成，且以字母开头。",
      ),
    value: z.string().max(4000, "值过长。"),
    clearValue: z.boolean(),
    description: z.string().trim().max(500, "说明不能超过 500 个字符。"),
    agents: z.array(z.string()),
    enabled: z.boolean(),
  })
  .superRefine((values, context) => {
    if (values.clearValue && values.value.length > 0) {
      context.addIssue({
        code: "custom",
        path: ["clearValue"],
        message: "不能同时更新和清除值。",
      });
    }
  });

type VariableFormValues = z.infer<typeof variableSchema>;

type EnvironmentVariableDialogProps = {
  variable?: EnvironmentVariableItem;
  onClose: () => void;
};

function toFormValues(variable: EnvironmentVariableItem | undefined): VariableFormValues {
  return {
    name: variable?.spec.name ?? "",
    value: "",
    clearValue: false,
    description: variable?.spec.description ?? "",
    agents: variable?.spec.agents ?? [],
    enabled: variable?.spec.enabled !== false,
  };
}

function toSpec(values: VariableFormValues): EnvironmentVariableSpec {
  return {
    name: values.name.trim(),
    description: values.description.trim() || undefined,
    enabled: values.enabled,
    agents: values.agents,
  };
}

export function EnvironmentVariableDialog({ variable, onClose }: EnvironmentVariableDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<VariableFormValues>({
    resolver: zodResolver(variableSchema) as Resolver<VariableFormValues>,
    defaultValues: toFormValues(variable),
  });

  const saveMutation = useMutation({
    mutationFn: async (values: VariableFormValues) => {
      const nextValue = values.value;
      if (!variable) {
        return createEnvironmentVariable({
          apiVersion: AI_API_VERSION,
          kind: AI_ENVIRONMENT_VARIABLE_KIND,
          metadata: { generateName: resourceGenerateName(values.name, "env") },
          spec: {
            ...toSpec(values),
            value: nextValue || undefined,
            configured: nextValue.length > 0,
          },
        });
      }
      const keepConfigured = variable.spec.configured === true && !values.clearValue;
      const saved = await updateEnvironmentVariable({
        ...variable,
        spec: {
          ...toSpec(values),
          configured: keepConfigured || nextValue.length > 0,
        },
      });
      const resourceName = saved.metadata.name;
      if (values.clearValue) {
        await clearEnvironmentVariableValue(resourceName);
      } else if (nextValue) {
        await updateEnvironmentVariableValue(resourceName, nextValue);
      }
      return saved;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ENVIRONMENT_VARIABLES_QUERY_KEY });
      toast.success(variable ? "环境变量已保存。" : "环境变量已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "环境变量")),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const configured = variable?.spec.configured === true;

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<KeyRound className="size-5" />}
      title={variable ? `编辑 ${variable.spec.name || variable.metadata.name}` : "新建环境变量"}
      description="变量值只写不读：保存后不会在列表或详情中展示。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-2xl"
      submitLabel={variable ? "保存更改" : "创建环境变量"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="变量名称"
            htmlFor="env-variable-name"
            hint="大写字母、数字与下划线，例如 OPENAI_API_KEY。"
            error={form.formState.errors.name?.message}
          >
            <Input
              id="env-variable-name"
              placeholder="OPENAI_API_KEY"
              className="font-mono"
              {...form.register("name")}
            />
          </FormField>
          <FormField
            label="值"
            htmlFor="env-variable-value"
            hint={
              variable
                ? configured
                  ? "留空表示保留当前值；写入后不再显示。"
                  : "留空表示暂不配置值。"
                : "可选；写入后不再显示。"
            }
            error={form.formState.errors.value?.message}
          >
            <Input
              id="env-variable-value"
              type="password"
              autoComplete="new-password"
              placeholder={variable && configured ? "保留当前值" : "输入变量值"}
              className="font-mono"
              {...form.register("value")}
            />
          </FormField>
        </div>
        <FormField
          label="说明"
          htmlFor="env-variable-description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            id="env-variable-description"
            rows={2}
            placeholder="说明该变量的用途。"
            {...form.register("description")}
          />
        </FormField>
        {variable && configured ? (
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Controller
                control={form.control}
                name="clearValue"
                render={({ field }) => (
                  <Checkbox
                    id="env-variable-clear"
                    checked={field.value}
                    onCheckedChange={(checked) => field.onChange(checked === true)}
                  />
                )}
              />
              <Label htmlFor="env-variable-clear">清除已配置的值</Label>
            </div>
            {form.formState.errors.clearValue?.message ? (
              <p className="text-xs text-destructive">{form.formState.errors.clearValue.message}</p>
            ) : null}
          </div>
        ) : null}
      </FormDialogSection>

      <FormDialogSection title="绑定">
        <Controller
          control={form.control}
          name="agents"
          render={({ field }) => <AgentsField value={field.value} onChange={field.onChange} />}
        />
        <Controller
          control={form.control}
          name="enabled"
          render={({ field }) => <EnabledField checked={field.value} onChange={field.onChange} />}
        />
      </FormDialogSection>
    </FormDialog>
  );
}
