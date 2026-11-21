import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FileText } from "lucide-react";
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
  AI_SKILL_KIND,
  SKILLS_QUERY_KEY,
  SKILL_SOURCES,
  type SkillDraft,
  type SkillItem,
  type SkillSource,
  type SkillSpec,
  type SkillStatus,
  createSkill,
  updateSkill,
} from "../lib/api";
import { SKILL_SOURCE_HINTS, SKILL_SOURCE_LABELS } from "../lib/model";

const skillSchema = z
  .object({
    displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
    description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
    source: z.enum(SKILL_SOURCES),
    url: z.string().trim().max(1000, "地址过长。"),
    ref: z.string().trim().max(200, "引用不能超过 200 个字符。"),
    path: z.string().trim().max(500, "路径过长。"),
    content: z.string(),
    agents: z.array(z.string()),
    enabled: z.boolean(),
  })
  .superRefine((values, context) => {
    if (values.source === "INLINE") {
      if (!values.content.trim()) {
        context.addIssue({
          code: "custom",
          path: ["content"],
          message: "内联技能必须填写 SKILL.md 内容。",
        });
      }
      return;
    }
    if (!values.url) {
      context.addIssue({
        code: "custom",
        path: ["url"],
        message: "URL 或 Git 来源必须填写地址。",
      });
    } else if (!/^https?:\/\//i.test(values.url)) {
      context.addIssue({
        code: "custom",
        path: ["url"],
        message: "地址必须以 http:// 或 https:// 开头。",
      });
    }
  });

type SkillFormValues = z.infer<typeof skillSchema>;

type SkillDialogProps = {
  skill?: SkillItem;
  draft?: SkillDraft;
  onClose: () => void;
};

function toFormValues(
  skill: SkillItem | undefined,
  draft: SkillDraft | undefined,
): SkillFormValues {
  const spec: SkillSpec | undefined = skill?.spec ?? draft?.spec;
  return {
    displayName: spec?.displayName ?? "",
    description: spec?.description ?? "",
    source: spec?.source ?? "INLINE",
    url: spec?.url ?? "",
    ref: spec?.ref ?? "",
    path: spec?.path ?? "",
    content: spec?.content ?? "",
    agents: spec?.agents ?? [],
    enabled: spec?.enabled !== false,
  };
}

function toSpec(values: SkillFormValues, status?: SkillStatus): SkillSpec {
  const source = values.source;
  const content = values.content.trim() ? values.content : undefined;
  return {
    displayName: values.displayName.trim(),
    description: values.description.trim() || undefined,
    source,
    url: source === "INLINE" ? undefined : values.url.trim(),
    ref: source === "INLINE" ? undefined : values.ref.trim() || undefined,
    path: source === "GIT" ? values.path.trim() || undefined : undefined,
    content,
    enabled: values.enabled,
    status,
    agents: values.agents,
  };
}

export function SkillDialog({ skill, draft, onClose }: SkillDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<SkillFormValues>({
    resolver: zodResolver(skillSchema) as Resolver<SkillFormValues>,
    defaultValues: toFormValues(skill, draft),
  });
  const source = useWatch({ control: form.control, name: "source" });

  const saveMutation = useMutation({
    mutationFn: (values: SkillFormValues) => {
      const spec = toSpec(values, skill?.spec.status);
      if (skill) {
        return updateSkill({ ...skill, spec });
      }
      return createSkill({
        apiVersion: AI_API_VERSION,
        kind: AI_SKILL_KIND,
        metadata: { generateName: resourceGenerateName(values.displayName, "skill") },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: SKILLS_QUERY_KEY });
      toast.success(skill ? "技能已保存。" : "技能已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "技能")),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const title = skill
    ? `编辑 ${skill.spec.displayName || skill.metadata.name}`
    : draft
      ? "保存导入的技能"
      : "新建技能";

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<FileText className="size-5" />}
      title={title}
      description={
        draft
          ? "已获取 SKILL.md 草稿，确认来源、内容与绑定关系后保存。"
          : "配置 SKILL.md 来源、内容与关联 Agent。"
      }
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-3xl"
      submitLabel={skill ? "保存更改" : "创建技能"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="skill-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="skill-display-name"
              placeholder="例如：提交规范"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="来源"
            htmlFor="skill-source"
            hint={SKILL_SOURCE_HINTS[source]}
            error={form.formState.errors.source?.message}
          >
            <Controller
              control={form.control}
              name="source"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as SkillSource)}
                >
                  <SelectTrigger id="skill-source" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SKILL_SOURCES.map((item) => (
                      <SelectItem key={item} value={item}>
                        {SKILL_SOURCE_LABELS[item]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        </div>
        <FormField
          label="描述"
          htmlFor="skill-description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            id="skill-description"
            rows={2}
            placeholder="说明该技能适用的任务。"
            {...form.register("description")}
          />
        </FormField>
      </FormDialogSection>

      {source !== "INLINE" ? (
        <FormDialogSection title="来源地址">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              label="地址"
              htmlFor="skill-url"
              hint="HTTP(S) 地址，例如 SKILL.md 或仓库地址。"
              error={form.formState.errors.url?.message}
            >
              <Input
                id="skill-url"
                placeholder="https://example.com/skills/commit/SKILL.md"
                className="font-mono"
                {...form.register("url")}
              />
            </FormField>
            <FormField
              label="引用"
              htmlFor="skill-ref"
              hint="可选，例如 main 或 v1.0.0。"
              error={form.formState.errors.ref?.message}
            >
              <Input
                id="skill-ref"
                placeholder="main"
                className="font-mono"
                {...form.register("ref")}
              />
            </FormField>
          </div>
          {source === "GIT" ? (
            <FormField
              label="仓库路径"
              htmlFor="skill-path"
              hint="可选，仓库中 SKILL.md 的相对路径。"
              error={form.formState.errors.path?.message}
            >
              <Input
                id="skill-path"
                placeholder="skills/commit/SKILL.md"
                className="font-mono"
                {...form.register("path")}
              />
            </FormField>
          ) : null}
        </FormDialogSection>
      ) : null}

      <FormDialogSection title="SKILL.md">
        <FormField
          label="内容"
          htmlFor="skill-content"
          hint={
            source === "INLINE"
              ? "内联技能必须填写完整的 SKILL.md 内容。"
              : "可粘贴 SKILL.md 内容；留空时该技能不会注入任何指令。"
          }
          error={form.formState.errors.content?.message}
        >
          <Textarea
            id="skill-content"
            rows={14}
            placeholder="# 技能名称&#10;&#10;描述触发条件、操作步骤与注意事项。"
            className="font-mono text-xs"
            {...form.register("content")}
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
