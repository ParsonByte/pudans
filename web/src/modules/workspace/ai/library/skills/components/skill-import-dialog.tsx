import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";

import { resolveLibraryApiError } from "../../lib/api";
import { type SkillDraft, importSkillFromUrl } from "../lib/api";

const importSchema = z.object({
  url: z
    .string()
    .trim()
    .min(1, "请输入 SKILL.md 的 URL。")
    .refine((value) => /^https?:\/\//i.test(value), "URL 必须以 http:// 或 https:// 开头。"),
  ref: z.string().trim().max(200, "引用不能超过 200 个字符。"),
});

type ImportFormValues = z.infer<typeof importSchema>;

type SkillImportDialogProps = {
  onImported: (draft: SkillDraft) => void;
  onClose: () => void;
};

export function SkillImportDialog({ onImported, onClose }: SkillImportDialogProps) {
  const form = useForm<ImportFormValues>({
    resolver: zodResolver(importSchema) as Resolver<ImportFormValues>,
    defaultValues: { url: "", ref: "" },
  });

  const importMutation = useMutation({
    mutationFn: (values: ImportFormValues) => importSkillFromUrl(values.url, values.ref),
    onSuccess: (draft) => {
      toast.success("已获取 SKILL.md 草稿，请确认内容后保存。");
      onImported(draft);
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "技能")),
  });

  const onSubmit = form.handleSubmit(
    (values) => importMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Download className="size-5" />}
      title="从 URL 导入技能"
      description="拉取 SKILL.md 并生成待保存的草稿；此步骤不会写入资源。"
      form={form}
      onSubmit={onSubmit}
      isPending={importMutation.isPending}
      contentClassName="sm:max-w-lg"
      submitLabel="获取草稿"
    >
      <FormField
        label="SKILL.md 地址"
        htmlFor="skill-import-url"
        hint="支持 HTTP(S) 地址，内容最大 1 MB。"
        error={form.formState.errors.url?.message}
      >
        <Input
          id="skill-import-url"
          placeholder="https://example.com/skills/commit/SKILL.md"
          className="font-mono"
          {...form.register("url")}
        />
      </FormField>
      <FormField
        label="引用"
        htmlFor="skill-import-ref"
        hint="可选，用于 Git 或带版本的 SKILL.md 地址。"
        error={form.formState.errors.ref?.message}
      >
        <Input
          id="skill-import-ref"
          placeholder="main"
          className="font-mono"
          {...form.register("ref")}
        />
      </FormField>
    </FormDialog>
  );
}
