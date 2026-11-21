import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { MessageSquare } from "lucide-react";
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
import type { KnowledgeBaseItem } from "@/modules/workspace/ai/knowledge/lib/api";
import { EnabledField } from "@/modules/workspace/ai/library/components/library-dialog-fields";
import { MultiSelect } from "@/modules/workspace/ai/library/components/multi-select";
import { resolveLibraryApiError } from "@/modules/workspace/ai/library/lib/api";
import { AI_API_VERSION, type AiRouteGroupItem } from "@/modules/workspace/ai/providers/lib/api";
import {
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "@/modules/workspace/ai/providers/lib/model";

import {
  AI_CHAT_MODE_KIND,
  type AiChatModeItem,
  type AiChatModeSpec,
  type AiChatModeTier,
  CHAT_MODES_QUERY_KEY,
  createChatMode,
  updateChatMode,
} from "../lib/api";
import {
  CHAT_MODE_TIER_OPTIONS,
  CHAT_MODE_TIER_VALUES,
  chatModeLabel,
  isBuiltinChatMode,
  knowledgeBaseOptions,
  routeGroupOptions,
} from "../lib/model";

const chatModeSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入模式 ID。")
    .max(63, "模式 ID 不能超过 63 个字符。")
    .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  routeGroup: z.string().trim().min(1, "请选择路由组。"),
  defaultTier: z.enum(CHAT_MODE_TIER_VALUES),
  knowledgeBaseCodes: z.array(z.string()),
  enabled: z.boolean(),
});

type ChatModeFormValues = z.infer<typeof chatModeSchema>;

type ChatModeDialogProps = {
  mode?: AiChatModeItem;
  modes: AiChatModeItem[];
  routeGroups: AiRouteGroupItem[];
  knowledgeBases: KnowledgeBaseItem[];
  routeGroupsLoading: boolean;
  knowledgeBasesLoading: boolean;
  routeGroupsFailed: boolean;
  knowledgeBasesFailed: boolean;
  onClose: () => void;
};

function toFormValues(mode: AiChatModeItem | undefined): ChatModeFormValues {
  return {
    name: mode?.metadata.name ?? "",
    displayName: mode?.spec.displayName ?? "",
    routeGroup: mode?.spec.routeGroup ?? "",
    defaultTier: mode?.spec.defaultTier ?? "AUTO",
    knowledgeBaseCodes: [...(mode?.spec.knowledgeBaseCodes ?? [])],
    enabled: mode?.spec.enabled !== false,
  };
}

function toSpec(values: ChatModeFormValues, mode: AiChatModeItem | undefined): AiChatModeSpec {
  return {
    ...(mode?.spec ?? {}),
    displayName: values.displayName.trim(),
    routeGroup: values.routeGroup,
    defaultTier: values.defaultTier,
    knowledgeBaseCodes: values.knowledgeBaseCodes,
    enabled: values.enabled,
  };
}

export function ChatModeDialog({
  mode,
  modes,
  routeGroups,
  knowledgeBases,
  routeGroupsLoading,
  knowledgeBasesLoading,
  routeGroupsFailed,
  knowledgeBasesFailed,
  onClose,
}: ChatModeDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<ChatModeFormValues>({
    resolver: zodResolver(chatModeSchema) as Resolver<ChatModeFormValues>,
    defaultValues: toFormValues(mode),
  });
  const groupOptions = routeGroupOptions(routeGroups, mode?.spec.routeGroup);

  const saveMutation = useMutation({
    mutationFn: (values: ChatModeFormValues) => {
      const spec = toSpec(values, mode);
      if (mode) {
        return updateChatMode({ ...mode, spec });
      }
      return createChatMode({
        apiVersion: AI_API_VERSION,
        kind: AI_CHAT_MODE_KIND,
        metadata: { name: values.name.trim() },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CHAT_MODES_QUERY_KEY });
      await queryClient.invalidateQueries({ queryKey: ["chat-modes"] });
      toast.success(mode ? "对话模式已保存。" : "对话模式已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "对话模式")),
  });

  const onSubmit = form.handleSubmit(
    (values) => {
      const name = values.name.trim();
      if (!mode && isBuiltinChatMode(name)) {
        toast.error("fast、pro 为内置对话模式名称，请更换。");
        return;
      }
      if (!mode && modes.some((item) => item.metadata.name === name)) {
        toast.error("模式 ID 已存在，请更换后重试。");
        return;
      }
      saveMutation.mutate(values);
    },
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<MessageSquare className="size-5" />}
      title={mode ? `编辑 ${chatModeLabel(mode)}` : "新建对话模式"}
      description="对话模式把用户可选的入口绑定到路由组、默认档位与知识库。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-2xl"
      submitLabel={mode ? "保存更改" : "创建模式"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="模式 ID"
            htmlFor="chat-mode-name"
            hint={mode ? "模式 ID 创建后不可修改。" : RESOURCE_NAME_HINT}
            error={form.formState.errors.name?.message}
          >
            <Input
              id="chat-mode-name"
              placeholder="例如：fast"
              disabled={Boolean(mode)}
              {...form.register("name")}
            />
          </FormField>
          <FormField
            label="名称"
            htmlFor="chat-mode-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="chat-mode-display-name"
              placeholder="例如：快速模式"
              {...form.register("displayName")}
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="路由">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="路由组"
            htmlFor="chat-mode-route-group"
            hint={
              routeGroupsFailed
                ? "无法加载路由组，请检查权限后重试。"
                : routeGroups.length === 0 && !routeGroupsLoading
                  ? "没有可用路由组，请先在路由组页面创建。"
                  : "该模式的所有请求都交给所选路由组处理。"
            }
            error={form.formState.errors.routeGroup?.message}
          >
            <Controller
              control={form.control}
              name="routeGroup"
              render={({ field }) => (
                <Select
                  value={field.value}
                  disabled={routeGroupsLoading}
                  onValueChange={field.onChange}
                >
                  <SelectTrigger id="chat-mode-route-group" className="w-full">
                    <SelectValue placeholder="选择路由组" />
                  </SelectTrigger>
                  <SelectContent>
                    {groupOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
          <FormField
            label="默认档位"
            htmlFor="chat-mode-default-tier"
            hint="自动档位会按路由组规则选择模型。"
            error={form.formState.errors.defaultTier?.message}
          >
            <Controller
              control={form.control}
              name="defaultTier"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as AiChatModeTier)}
                >
                  <SelectTrigger id="chat-mode-default-tier" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CHAT_MODE_TIER_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        </div>
      </FormDialogSection>

      <FormDialogSection title="知识库">
        <FormField
          label="知识库"
          htmlFor="chat-mode-knowledge-bases"
          hint={
            knowledgeBasesFailed
              ? "无法加载知识库列表，请检查权限后重试。"
              : knowledgeBases.length === 0 && !knowledgeBasesLoading
                ? "没有可选知识库，本模式不会自动检索知识库。"
                : "选择该模式默认检索的知识库；可同时选择多个。"
          }
          error={form.formState.errors.knowledgeBaseCodes?.message}
        >
          <Controller
            control={form.control}
            name="knowledgeBaseCodes"
            render={({ field }) => (
              <MultiSelect
                id="chat-mode-knowledge-bases"
                value={field.value}
                onChange={field.onChange}
                options={knowledgeBaseOptions(knowledgeBases, field.value)}
                placeholder="选择知识库"
                emptyMessage="没有可选知识库"
                searchPlaceholder="搜索知识库"
                loading={knowledgeBasesLoading}
              />
            )}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="可用性">
        <Controller
          control={form.control}
          name="enabled"
          render={({ field }) => (
            <EnabledField
              checked={field.value}
              onChange={field.onChange}
              hint="停用后该模式不再出现在用户的对话模式选择器中。"
            />
          )}
        />
      </FormDialogSection>
    </FormDialog>
  );
}
