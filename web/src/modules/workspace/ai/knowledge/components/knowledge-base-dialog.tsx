import { useState } from "react";
import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen } from "lucide-react";
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
import { Textarea } from "@/components/ui/textarea";
import {
  ProviderDialogSection,
  ProviderDialogShell,
  ProviderFieldRow,
} from "@/modules/workspace/ai/providers/components/provider-dialog-shell";
import { AI_API_VERSION } from "@/modules/workspace/ai/providers/lib/api";

import {
  KNOWLEDGE_BASE_KIND,
  type KnowledgeBaseChunkStrategy,
  type KnowledgeBaseItem,
  type KnowledgeBaseSpec,
  type KnowledgeBaseVisibility,
  createKnowledgeBase,
  resolveKnowledgeBaseApiError,
  updateKnowledgeBase,
} from "../lib/api";
import {
  CHUNK_STRATEGY_OPTIONS,
  VISIBILITY_HINTS,
  VISIBILITY_OPTIONS,
  generateKnowledgeBaseName,
} from "../lib/model";

const knowledgeBaseSchema = z
  .object({
    displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
    description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
    visibility: z.enum(["PRIVATE", "TEAM", "COMPANY"]),
    embeddingModel: z.string().trim().min(1, "请输入向量模型编码。").max(200, "向量模型编码过长。"),
    chunkStrategy: z.enum(["RECURSIVE", "SEMANTIC", "FIXED"]),
    chunkSize: z.coerce.number().int().min(1, "切片大小必须大于 0。").max(10000, "切片大小过大。"),
    chunkOverlap: z.coerce
      .number()
      .int()
      .min(0, "切片重叠不能为负数。")
      .max(9999, "切片重叠过大。"),
    topK: z.coerce.number().int().min(1, "召回数量必须大于 0。").max(50, "召回数量最多 50。"),
    scoreThreshold: z.coerce.number().min(0, "分数阈值不能小于 0。").max(1, "分数阈值不能大于 1。"),
    charBudget: z.coerce
      .number()
      .int()
      .min(1, "字符预算必须大于 0。")
      .max(100000, "字符预算最多 100000。"),
    rerankEnabled: z.boolean(),
    rerankModel: z.string().trim().max(200, "重排模型编码过长。"),
    status: z.enum(["ENABLED", "DISABLED"]),
  })
  .superRefine((values, context) => {
    if (values.chunkOverlap >= values.chunkSize) {
      context.addIssue({
        code: "custom",
        path: ["chunkOverlap"],
        message: "切片重叠必须小于切片大小。",
      });
    }
    if (values.rerankEnabled && !values.rerankModel) {
      context.addIssue({
        code: "custom",
        path: ["rerankModel"],
        message: "启用重排后必须填写重排模型编码。",
      });
    }
  });

type KnowledgeBaseFormValues = z.infer<typeof knowledgeBaseSchema>;

type KnowledgeBaseDialogProps = {
  knowledgeBase?: KnowledgeBaseItem;
  onClose: () => void;
  onSaved: (item: KnowledgeBaseItem) => void;
};

export function KnowledgeBaseDialog({ knowledgeBase, onClose, onSaved }: KnowledgeBaseDialogProps) {
  const queryClient = useQueryClient();
  const [resourceName] = useState(
    () =>
      knowledgeBase?.metadata.name ??
      generateKnowledgeBaseName(knowledgeBase?.spec.displayName ?? ""),
  );
  const form = useForm<KnowledgeBaseFormValues>({
    resolver: zodResolver(knowledgeBaseSchema) as Resolver<KnowledgeBaseFormValues>,
    defaultValues: toFormValues(knowledgeBase),
  });
  const visibility = useWatch({ control: form.control, name: "visibility" });
  const rerankEnabled = useWatch({ control: form.control, name: "rerankEnabled" });
  const currentUserQuery = useQuery({
    queryKey: ["account-profile"],
    queryFn: async () => (await apiClient.user.getCurrentUser()).data,
    retry: false,
  });

  const saveMutation = useMutation({
    mutationFn: (item: KnowledgeBaseItem) =>
      knowledgeBase ? updateKnowledgeBase(item) : createKnowledgeBase(item),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
      toast.success(knowledgeBase ? "知识库已保存。" : "知识库已创建。");
      onSaved(saved);
      onClose();
    },
    onError: (error) => toast.error(resolveKnowledgeBaseApiError(error)),
  });

  const submit = form.handleSubmit((values) => {
    const currentUid = currentUserQuery.data?.spec.uid;
    const ownerUserId = knowledgeBase?.spec.ownerUserId ?? currentUid;
    if (values.visibility === "PRIVATE" && !ownerUserId) {
      form.setError("visibility", {
        message: "无法获取当前用户信息，请选择公司可见或稍后重试。",
      });
      return;
    }
    const spec: KnowledgeBaseSpec = {
      ...(knowledgeBase?.spec ?? {}),
      displayName: values.displayName.trim(),
      description: values.description.trim() || undefined,
      visibility: values.visibility,
      ownerUserId,
      embeddingModel: values.embeddingModel.trim(),
      chunkStrategy: values.chunkStrategy,
      chunkSize: values.chunkSize,
      chunkOverlap: values.chunkOverlap,
      topK: values.topK,
      scoreThreshold: values.scoreThreshold,
      charBudget: values.charBudget,
      rerankEnabled: values.rerankEnabled,
      rerankModel: values.rerankEnabled ? values.rerankModel.trim() || undefined : undefined,
      status: values.status,
    };
    saveMutation.mutate({
      apiVersion: AI_API_VERSION,
      kind: KNOWLEDGE_BASE_KIND,
      metadata: { ...(knowledgeBase?.metadata ?? {}), name: resourceName },
      spec,
    });
  });

  return (
    <ProviderDialogShell
      title={knowledgeBase ? "编辑知识库" : "新建知识库"}
      description="配置知识库的可见范围、向量模型、切片与检索参数。"
      icon={<BookOpen className="size-4" />}
      onSubmit={submit}
      onClose={onClose}
      isPending={saveMutation.isPending}
      submitLabel={knowledgeBase ? "保存" : "创建"}
    >
      <ProviderDialogSection title="基本信息">
        <ProviderFieldRow label="名称" error={form.formState.errors.displayName?.message}>
          <Input
            placeholder="例如：产品手册"
            aria-invalid={Boolean(form.formState.errors.displayName)}
            {...form.register("displayName")}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="描述" error={form.formState.errors.description?.message}>
          <Textarea
            placeholder="说明知识库的内容与用途"
            rows={3}
            {...form.register("description")}
          />
        </ProviderFieldRow>
        <ProviderFieldRow
          label="可见范围"
          hint={VISIBILITY_HINTS[visibility]}
          error={form.formState.errors.visibility?.message}
        >
          <Controller
            control={form.control}
            name="visibility"
            render={({ field }) => (
              <Select
                value={field.value}
                onValueChange={(value) => field.onChange(value as KnowledgeBaseVisibility)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {VISIBILITY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="资源 ID" hint="创建后自动生成，用于聊天模式与接口引用。">
          <Input value={resourceName} disabled />
        </ProviderFieldRow>
      </ProviderDialogSection>

      <ProviderDialogSection title="向量与切片">
        <ProviderFieldRow
          label="向量模型"
          hint="填写模型管理中的向量模型编码；未配置时仍保留关键词检索。"
          error={form.formState.errors.embeddingModel?.message}
        >
          <Input
            placeholder="例如：text-embedding-3-small"
            aria-invalid={Boolean(form.formState.errors.embeddingModel)}
            {...form.register("embeddingModel")}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="切片策略" error={form.formState.errors.chunkStrategy?.message}>
          <Controller
            control={form.control}
            name="chunkStrategy"
            render={({ field }) => (
              <Select
                value={field.value}
                onValueChange={(value) => field.onChange(value as KnowledgeBaseChunkStrategy)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CHUNK_STRATEGY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value} disabled={option.disabled}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="切片大小" error={form.formState.errors.chunkSize?.message}>
          <Input
            type="number"
            min={1}
            max={10000}
            aria-invalid={Boolean(form.formState.errors.chunkSize)}
            {...form.register("chunkSize", { valueAsNumber: true })}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="切片重叠" error={form.formState.errors.chunkOverlap?.message}>
          <Input
            type="number"
            min={0}
            max={9999}
            aria-invalid={Boolean(form.formState.errors.chunkOverlap)}
            {...form.register("chunkOverlap", { valueAsNumber: true })}
          />
        </ProviderFieldRow>
      </ProviderDialogSection>

      <ProviderDialogSection title="检索参数">
        <ProviderFieldRow label="召回数量" error={form.formState.errors.topK?.message}>
          <Input
            type="number"
            min={1}
            max={50}
            aria-invalid={Boolean(form.formState.errors.topK)}
            {...form.register("topK", { valueAsNumber: true })}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="分数阈值" error={form.formState.errors.scoreThreshold?.message}>
          <Input
            type="number"
            min={0}
            max={1}
            step={0.05}
            aria-invalid={Boolean(form.formState.errors.scoreThreshold)}
            {...form.register("scoreThreshold", { valueAsNumber: true })}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="字符预算" error={form.formState.errors.charBudget?.message}>
          <Input
            type="number"
            min={1}
            max={100000}
            aria-invalid={Boolean(form.formState.errors.charBudget)}
            {...form.register("charBudget", { valueAsNumber: true })}
          />
        </ProviderFieldRow>
        <ProviderFieldRow label="启用重排">
          <Controller
            control={form.control}
            name="rerankEnabled"
            render={({ field }) => (
              <Switch
                checked={field.value}
                onCheckedChange={field.onChange}
                aria-label="启用重排"
              />
            )}
          />
        </ProviderFieldRow>
        {rerankEnabled ? (
          <ProviderFieldRow label="重排模型" error={form.formState.errors.rerankModel?.message}>
            <Input
              placeholder="例如：gte-rerank"
              aria-invalid={Boolean(form.formState.errors.rerankModel)}
              {...form.register("rerankModel")}
            />
          </ProviderFieldRow>
        ) : null}
      </ProviderDialogSection>

      <ProviderDialogSection title="状态">
        <ProviderFieldRow label="启用" hint="停用后该知识库将不再出现在可读列表，检索也不会命中。">
          <Controller
            control={form.control}
            name="status"
            render={({ field }) => (
              <Switch
                checked={field.value === "ENABLED"}
                onCheckedChange={(checked) => field.onChange(checked ? "ENABLED" : "DISABLED")}
                aria-label="启用知识库"
              />
            )}
          />
        </ProviderFieldRow>
      </ProviderDialogSection>
    </ProviderDialogShell>
  );
}

function toFormValues(knowledgeBase?: KnowledgeBaseItem): KnowledgeBaseFormValues {
  const spec = knowledgeBase?.spec;
  const chunkStrategy: KnowledgeBaseChunkStrategy =
    spec?.chunkStrategy === "FIXED" || spec?.chunkStrategy === "SEMANTIC"
      ? spec.chunkStrategy
      : "RECURSIVE";
  return {
    displayName: spec?.displayName ?? "",
    description: spec?.description ?? "",
    visibility: spec?.visibility ?? "COMPANY",
    embeddingModel: spec?.embeddingModel ?? "",
    chunkStrategy,
    chunkSize: spec?.chunkSize ?? 800,
    chunkOverlap: spec?.chunkOverlap ?? 120,
    topK: spec?.topK ?? 5,
    scoreThreshold: spec?.scoreThreshold ?? 0.3,
    charBudget: spec?.charBudget ?? 4000,
    rerankEnabled: spec?.rerankEnabled ?? false,
    rerankModel: spec?.rerankModel ?? "",
    status: spec?.status ?? "ENABLED",
  };
}
