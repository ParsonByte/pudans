import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { TerminalSquare } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

import { AgentsField, EnabledField } from "../../components/library-dialog-fields";
import { resolveLibraryApiError } from "../../lib/api";
import {
  AI_API_VERSION,
  AI_SLASH_COMMAND_KIND,
  SLASH_COMMANDS_QUERY_KEY,
  SLASH_COMMAND_NAME_PATTERN,
  type SlashCommandItem,
  type SlashCommandSpec,
  createSlashCommand,
  updateSlashCommand,
} from "../lib/api";

const slashCommandSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入命令名称。")
    .max(64, "名称不能超过 64 个字符。")
    .refine(
      (value) => SLASH_COMMAND_NAME_PATTERN.test(value),
      "名称只能包含小写字母、数字与连字符，且以字母或数字开头。",
    ),
  description: z.string().trim().max(500, "说明不能超过 500 个字符。"),
  prompt: z.string().trim().min(1, "请输入命令提示词。").max(20000, "提示词过长。"),
  argumentHint: z.string().trim().max(200, "参数提示不能超过 200 个字符。"),
  agents: z.array(z.string()),
  enabled: z.boolean(),
});

type SlashCommandFormValues = z.infer<typeof slashCommandSchema>;

type SlashCommandDialogProps = {
  command?: SlashCommandItem;
  onClose: () => void;
};

function toFormValues(command: SlashCommandItem | undefined): SlashCommandFormValues {
  return {
    name: command?.spec.name ?? "",
    description: command?.spec.description ?? "",
    prompt: command?.spec.prompt ?? "",
    argumentHint: command?.spec.argumentHint ?? "",
    agents: command?.spec.agents ?? [],
    enabled: command?.spec.enabled !== false,
  };
}

function toSpec(values: SlashCommandFormValues): SlashCommandSpec {
  return {
    name: values.name.trim(),
    description: values.description.trim() || undefined,
    prompt: values.prompt.trim(),
    argumentHint: values.argumentHint.trim() || undefined,
    enabled: values.enabled,
    agents: values.agents,
  };
}

export function SlashCommandDialog({ command, onClose }: SlashCommandDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<SlashCommandFormValues>({
    resolver: zodResolver(slashCommandSchema) as Resolver<SlashCommandFormValues>,
    defaultValues: toFormValues(command),
  });

  const saveMutation = useMutation({
    mutationFn: (values: SlashCommandFormValues) => {
      const spec = toSpec(values);
      if (command) {
        return updateSlashCommand({ ...command, spec });
      }
      return createSlashCommand({
        apiVersion: AI_API_VERSION,
        kind: AI_SLASH_COMMAND_KIND,
        metadata: { generateName: `${spec.name}-` },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: SLASH_COMMANDS_QUERY_KEY });
      toast.success(command ? "斜杠命令已保存。" : "斜杠命令已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "斜杠命令")),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<TerminalSquare className="size-5" />}
      title={command ? `编辑 /${command.spec.name || command.metadata.name}` : "新建斜杠命令"}
      description="定义聊天输入框中可调用的命令模板与参数展开方式。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-2xl"
      submitLabel={command ? "保存更改" : "创建命令"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="命令名称"
            htmlFor="slash-command-name"
            hint="不含斜杠；小写字母、数字与连字符，例如 review-pr。"
            error={form.formState.errors.name?.message}
          >
            <Input
              id="slash-command-name"
              placeholder="review-pr"
              className="font-mono"
              {...form.register("name")}
            />
          </FormField>
          <FormField
            label="参数提示"
            htmlFor="slash-command-argument-hint"
            hint="可选，聊天输入框中展示的参数用法。"
            error={form.formState.errors.argumentHint?.message}
          >
            <Input
              id="slash-command-argument-hint"
              placeholder="[仓库名] [分支]"
              {...form.register("argumentHint")}
            />
          </FormField>
        </div>
        <FormField
          label="说明"
          htmlFor="slash-command-description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            id="slash-command-description"
            rows={2}
            placeholder="说明该命令的作用。"
            {...form.register("description")}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="提示词">
        <FormField
          label="提示词"
          htmlFor="slash-command-prompt"
          hint="支持 $ARGUMENTS 展开完整参数，$1…$9 展开第 N 个参数。"
          error={form.formState.errors.prompt?.message}
        >
          <Textarea
            id="slash-command-prompt"
            rows={8}
            placeholder="请审查 $1 分支的变更，重点关注 $ARGUMENTS。"
            className="font-mono text-xs"
            {...form.register("prompt")}
          />
        </FormField>
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
