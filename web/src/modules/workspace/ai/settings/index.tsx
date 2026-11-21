import { useEffect, useId, useState } from "react";
import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, Loader2, RefreshCw, Save, Undo2 } from "lucide-react";
import { toast } from "sonner";

import {
  type ContentPreview,
  ContentPreviewDialog,
  codeFence,
} from "@/components/content/content-preview";
import { Button } from "@/components/ui/button";
import { resolveApiErrorWithDetail } from "@/lib/api-error";
import { ConsolePage, ConsoleSection } from "@/modules/workspace/console/components/console-page";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { DefaultsSection } from "./components/defaults-section";
import { EmbeddingSection } from "./components/embedding-section";
import { GatewaySection } from "./components/gateway-section";
import { MemorySection } from "./components/memory-section";
import { PromptSection } from "./components/prompt-section";
import { RetrievalSection } from "./components/retrieval-section";
import { RoutingSection } from "./components/routing-section";
import {
  AI_SETTING_QUERY_KEY,
  type AiSettingRecord,
  fetchAiSettingRecord,
  saveAiSetting,
} from "./lib/api";
import { type SettingsFormValues, settingsSchema, toFormValues, toSettingSpec } from "./lib/schema";

const PAGE_TITLE = "AI 设置";

function resolveSettingsError(error: unknown): string {
  return resolveApiErrorWithDetail(error, {
    notFound: "AI 设置不存在，请确认系统初始化已完成。",
    conflict: "AI 设置已被其他操作修改，请刷新后重试。",
  });
}

export function AiSettingsView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");
  const canUpdate = allowedOperations.has("update");
  const formId = useId();
  const [preview, setPreview] = useState<ContentPreview | null>(null);

  const settingQuery = useQuery({
    queryKey: AI_SETTING_QUERY_KEY,
    queryFn: fetchAiSettingRecord,
    retry: false,
  });
  const setting = settingQuery.data;

  const form = useForm<SettingsFormValues>({
    resolver: zodResolver(settingsSchema) as Resolver<SettingsFormValues>,
    defaultValues: toFormValues(setting),
  });

  useEffect(() => {
    if (setting) {
      form.reset(toFormValues(setting));
    }
  }, [form, setting]);

  const saveMutation = useMutation({
    mutationFn: (values: SettingsFormValues) => {
      if (!setting) {
        throw new Error("AI 设置尚未加载。");
      }
      return saveAiSetting({ ...setting, spec: toSettingSpec(values, setting) });
    },
    onSuccess: async (updated: AiSettingRecord) => {
      queryClient.setQueryData(AI_SETTING_QUERY_KEY, updated);
      form.reset(toFormValues(updated));
      await queryClient.invalidateQueries({ queryKey: ["ai-settings"] });
      toast.success("AI 设置已保存。");
    },
    onError: (error) => toast.error(resolveSettingsError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const resetForm = () => {
    form.reset(toFormValues(setting));
  };

  const showPreview = form.handleSubmit(
    (values) => {
      if (!setting) {
        return;
      }
      setPreview({
        title: "AI 设置预览",
        description: "当前表单将保存到 /aisettings/ai-setting 的 spec。",
        content: codeFence(JSON.stringify(toSettingSpec(values, setting), null, 2), "json"),
      });
    },
    () => toast.error("请先修正表单错误再预览。"),
  );

  if (!canView) {
    return (
      <ConsolePage title={PAGE_TITLE}>
        <ConsoleState variant="permission" title="没有查看 AI 设置的权限。" wrapper />
      </ConsolePage>
    );
  }

  return (
    <ConsolePage
      title={PAGE_TITLE}
      description="集中管理 AI 的默认项、网关、向量嵌入、检索、提示词预算、路由与记忆策略；保存会替换整个 ai-setting 资源的 spec。"
      actions={
        <>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void settingQuery.refetch()}
            disabled={settingQuery.isFetching}
          >
            {settingQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={showPreview}
            disabled={!setting}
          >
            <Eye />
            预览
          </Button>
          {canUpdate ? (
            <>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={resetForm}
                disabled={!form.formState.isDirty || saveMutation.isPending}
              >
                <Undo2 />
                重置
              </Button>
              <Button
                type="submit"
                form={formId}
                size="sm"
                disabled={!form.formState.isDirty || saveMutation.isPending}
              >
                {saveMutation.isPending ? <Loader2 className="animate-spin" /> : <Save />}
                保存
              </Button>
            </>
          ) : null}
        </>
      }
    >
      {settingQuery.isPending ? (
        <ConsoleSection>
          <ConsoleBlockSkeleton rows={8} className="space-y-3 p-5" itemClassName="h-12 w-full" />
        </ConsoleSection>
      ) : settingQuery.isError || !setting ? (
        <ConsoleState
          variant="error"
          title="无法读取 AI 设置"
          description={
            settingQuery.error ? resolveSettingsError(settingQuery.error) : "AI 设置为空。"
          }
          onRetry={() => void settingQuery.refetch()}
          wrapper
        />
      ) : (
        <form id={formId} onSubmit={onSubmit} noValidate>
          <fieldset className="m-0 min-w-0 space-y-5 border-0 p-0" disabled={!canUpdate}>
            <DefaultsSection form={form} />
            <GatewaySection form={form} />
            <EmbeddingSection form={form} />
            <RetrievalSection form={form} />
            <PromptSection form={form} />
            <RoutingSection form={form} />
            <MemorySection form={form} />
          </fieldset>
        </form>
      )}

      <ContentPreviewDialog preview={preview} onClose={() => setPreview(null)} />
    </ConsolePage>
  );
}
