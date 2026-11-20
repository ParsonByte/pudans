import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ScrollText } from "lucide-react";
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
import { Textarea } from "@/components/ui/textarea";

import { AgentsField, EnabledField } from "../../components/library-dialog-fields";
import { resolveLibraryApiError } from "../../lib/api";
import { resourceGenerateName } from "../../lib/model";
import {
  AI_API_VERSION,
  AI_INSTRUCTION_KIND,
  INSTRUCTIONS_QUERY_KEY,
  INSTRUCTION_SCOPES,
  type InstructionItem,
  type InstructionScope,
  type InstructionSpec,
  createInstruction,
  updateInstruction,
} from "../lib/api";
import { INSTRUCTION_SCOPE_HINTS, INSTRUCTION_SCOPE_LABELS } from "../lib/model";

const instructionSchema = z.object({
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  scope: z.enum(INSTRUCTION_SCOPES),
  content: z.string().trim().min(1, "请输入指令内容。").max(50000, "指令内容过长。"),
  agents: z.array(z.string()),
  enabled: z.boolean(),
});

type InstructionFormValues = z.infer<typeof instructionSchema>;

type InstructionDialogProps = {
  instruction?: InstructionItem;
  onClose: () => void;
};

function toFormValues(instruction: InstructionItem | undefined): InstructionFormValues {
  return {
    displayName: instruction?.spec.displayName ?? "",
    scope: instruction?.spec.scope ?? "SHARED",
    content: instruction?.spec.content ?? "",
    agents: instruction?.spec.agents ?? [],
    enabled: instruction?.spec.enabled !== false,
  };
}

function toSpec(values: InstructionFormValues): InstructionSpec {
  return {
    displayName: values.displayName.trim(),
    scope: values.scope,
    content: values.content.trim(),
    enabled: values.enabled,
    agents: values.scope === "AGENT" ? values.agents : [],
  };
}

export function InstructionDialog({ instruction, onClose }: InstructionDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<InstructionFormValues>({
    resolver: zodResolver(instructionSchema) as Resolver<InstructionFormValues>,
    defaultValues: toFormValues(instruction),
  });
  const scope = useWatch({ control: form.control, name: "scope" });

  const saveMutation = useMutation({
    mutationFn: (values: InstructionFormValues) => {
      const spec = toSpec(values);
      if (instruction) {
        return updateInstruction({ ...instruction, spec });
      }
      return createInstruction({
        apiVersion: AI_API_VERSION,
        kind: AI_INSTRUCTION_KIND,
        metadata: { generateName: resourceGenerateName(values.displayName, "instruction") },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: INSTRUCTIONS_QUERY_KEY });
      toast.success(instruction ? "指令已保存。" : "指令已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "指令")),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<ScrollText className="size-5" />}
      title={
        instruction
          ? `编辑 ${instruction.spec.displayName || instruction.metadata.name}`
          : "新建指令"
      }
      description="系统指令会拼接到 Agent 的提示词中，可全局共享或按 Agent 下发。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-3xl"
      submitLabel={instruction ? "保存更改" : "创建指令"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="instruction-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="instruction-display-name"
              placeholder="例如：输出格式约定"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="生效范围"
            htmlFor="instruction-scope"
            hint={INSTRUCTION_SCOPE_HINTS[scope]}
            error={form.formState.errors.scope?.message}
          >
            <Controller
              control={form.control}
              name="scope"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as InstructionScope)}
                >
                  <SelectTrigger id="instruction-scope" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {INSTRUCTION_SCOPES.map((item) => (
                      <SelectItem key={item} value={item}>
                        {INSTRUCTION_SCOPE_LABELS[item]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        </div>
        <FormField
          label="指令内容"
          htmlFor="instruction-content"
          hint="写入系统提示词的多行文本。"
          error={form.formState.errors.content?.message}
        >
          <Textarea
            id="instruction-content"
            rows={12}
            placeholder="所有回答使用简体中文，并在结尾列出参考来源。"
            className="font-mono text-xs"
            {...form.register("content")}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="绑定">
        {scope === "AGENT" ? (
          <Controller
            control={form.control}
            name="agents"
            render={({ field }) => <AgentsField value={field.value} onChange={field.onChange} />}
          />
        ) : (
          <div className="border p-4">
            <p className="text-sm font-medium">共享指令</p>
            <p className="text-xs text-muted-foreground">
              共享指令对所有 Agent 生效，无需绑定具体 Agent。
            </p>
          </div>
        )}
        <Controller
          control={form.control}
          name="enabled"
          render={({ field }) => <EnabledField checked={field.value} onChange={field.onChange} />}
        />
      </FormDialogSection>
    </FormDialog>
  );
}
