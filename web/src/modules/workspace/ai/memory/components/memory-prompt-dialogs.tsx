import { useState } from "react";
import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Plus } from "lucide-react";
import { z } from "zod";

import { DialogFormShell } from "@/components/dialog-form-shell";
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
import { Textarea } from "@/components/ui/textarea";
import {
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "@/modules/workspace/ai/providers/lib/model";

import type { MemoryPromptLayer } from "../lib/api";
import { MEMORY_PROMPT_LAYER_OPTIONS } from "../lib/model";

const promptSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入资源名称。")
    .max(63, "资源名称不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  displayName: z.string().trim().min(1, "请输入显示名称。").max(100, "显示名称过长。"),
  layer: z.enum(["L1", "L2", "L3"]),
  prompt: z.string().trim().min(1, "请输入提示词内容。").max(20000, "提示词内容过长。"),
});

export type MemoryPromptCreateValues = z.infer<typeof promptSchema>;

type MemoryPromptCreateDialogProps = {
  pending: boolean;
  onCreate: (values: MemoryPromptCreateValues) => void;
  onClose: () => void;
};

export function MemoryPromptCreateDialog({
  pending,
  onCreate,
  onClose,
}: MemoryPromptCreateDialogProps) {
  const form = useForm<MemoryPromptCreateValues>({
    resolver: zodResolver(promptSchema) as Resolver<MemoryPromptCreateValues>,
    defaultValues: { name: "", displayName: "", layer: "L1", prompt: "" },
  });
  const layer = useWatch({ control: form.control, name: "layer" });

  return (
    <FormDialog
      open
      onClose={onClose}
      title="新建记忆提示词"
      description="创建提示词资源后，可在资源上应用为实例、团队或 Agent 策略。"
      icon={<Plus />}
      form={form}
      onSubmit={form.handleSubmit(
        (values) => onCreate(values),
        () => undefined,
      )}
      isPending={pending}
      submitLabel="创建提示词"
      maxHeightRatio={0.6}
    >
      <FormDialogSection title="基础信息">
        <div className="space-y-3">
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">资源名称</span>
            <Input placeholder="memory-strategy" {...form.register("name")} />
            {form.formState.errors.name ? (
              <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
            ) : null}
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">显示名称</span>
            <Input {...form.register("displayName")} />
            {form.formState.errors.displayName ? (
              <p className="text-xs text-destructive">
                {form.formState.errors.displayName.message}
              </p>
            ) : null}
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">记忆层级</span>
            <Select
              value={layer}
              onValueChange={(value) =>
                form.setValue("layer", value as MemoryPromptLayer, { shouldDirty: true })
              }
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {MEMORY_PROMPT_LAYER_OPTIONS.map((option) => (
                  <SelectItem key={option} value={option}>
                    {option}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
        </div>
      </FormDialogSection>
      <FormDialogSection title="提示词内容">
        <Textarea
          rows={12}
          className="font-mono text-[13px] leading-relaxed"
          {...form.register("prompt")}
        />
        {form.formState.errors.prompt ? (
          <p className="text-xs text-destructive">{form.formState.errors.prompt.message}</p>
        ) : null}
      </FormDialogSection>
    </FormDialog>
  );
}

type MemoryPromptRenameDialogProps = {
  currentName: string;
  pending: boolean;
  onRename: (displayName: string) => void;
  onClose: () => void;
};

export function MemoryPromptRenameDialog({
  currentName,
  pending,
  onRename,
  onClose,
}: MemoryPromptRenameDialogProps) {
  const [displayName, setDisplayName] = useState(currentName);

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="重命名提示词"
      description="修改提示词资源的显示名称，资源标识保持不变。"
      size="md"
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button
            type="button"
            disabled={!displayName.trim() || pending}
            onClick={() => onRename(displayName.trim())}
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            保存名称
          </Button>
        </>
      }
    >
      <FormField label="显示名称" htmlFor="memory-prompt-display-name">
        <Input
          id="memory-prompt-display-name"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          disabled={pending}
          autoFocus
        />
      </FormField>
    </DialogFormShell>
  );
}
