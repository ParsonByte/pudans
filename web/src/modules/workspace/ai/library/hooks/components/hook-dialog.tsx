import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Webhook } from "lucide-react";
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
  AI_HOOK_KIND,
  HOOKS_QUERY_KEY,
  HOOK_EVENTS,
  type HookEvent,
  type HookItem,
  type HookSpec,
  createHook,
  updateHook,
} from "../lib/api";
import { HOOK_EVENT_HINTS, HOOK_EVENT_LABELS } from "../lib/model";

const hookSchema = z.object({
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
  event: z.enum(HOOK_EVENTS),
  matcher: z.string().trim().max(200, "匹配器不能超过 200 个字符。"),
  command: z.string().trim().min(1, "请输入 Hook 命令。").max(2000, "命令过长。"),
  timeoutSeconds: z.coerce
    .number()
    .int()
    .min(1, "超时时间至少为 1 秒。")
    .max(600, "超时时间不能超过 600 秒。"),
  agents: z.array(z.string()),
  enabled: z.boolean(),
});

type HookFormValues = z.infer<typeof hookSchema>;

type HookDialogProps = {
  hook?: HookItem;
  onClose: () => void;
};

function toFormValues(hook: HookItem | undefined): HookFormValues {
  return {
    displayName: hook?.spec.displayName ?? "",
    description: hook?.spec.description ?? "",
    event: hook?.spec.event ?? "SESSION_START",
    matcher: hook?.spec.matcher ?? "",
    command: hook?.spec.command ?? "",
    timeoutSeconds: hook?.spec.timeoutSeconds ?? 60,
    agents: hook?.spec.agents ?? [],
    enabled: hook?.spec.enabled !== false,
  };
}

function toSpec(values: HookFormValues): HookSpec {
  return {
    displayName: values.displayName.trim(),
    description: values.description.trim() || undefined,
    event: values.event,
    matcher: values.matcher.trim() || undefined,
    command: values.command.trim(),
    timeoutSeconds: values.timeoutSeconds,
    enabled: values.enabled,
    agents: values.agents,
  };
}

export function HookDialog({ hook, onClose }: HookDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<HookFormValues>({
    resolver: zodResolver(hookSchema) as Resolver<HookFormValues>,
    defaultValues: toFormValues(hook),
  });
  const event = useWatch({ control: form.control, name: "event" });

  const saveMutation = useMutation({
    mutationFn: (values: HookFormValues) => {
      const spec = toSpec(values);
      if (hook) {
        return updateHook({ ...hook, spec });
      }
      return createHook({
        apiVersion: AI_API_VERSION,
        kind: AI_HOOK_KIND,
        metadata: { generateName: resourceGenerateName(values.displayName, "hook") },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: HOOKS_QUERY_KEY });
      toast.success(hook ? "Hook 已保存。" : "Hook 已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "Hook")),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Webhook className="size-5" />}
      title={hook ? `编辑 ${hook.spec.displayName || hook.metadata.name}` : "新建 Hook"}
      description="在 Agent 生命周期的关键事件上订阅外部自动化。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-2xl"
      submitLabel={hook ? "保存更改" : "创建 Hook"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="hook-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="hook-display-name"
              placeholder="例如：工具调用审计"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="事件"
            htmlFor="hook-event"
            hint={HOOK_EVENT_HINTS[event]}
            error={form.formState.errors.event?.message}
          >
            <Controller
              control={form.control}
              name="event"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as HookEvent)}
                >
                  <SelectTrigger id="hook-event" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {HOOK_EVENTS.map((item) => (
                      <SelectItem key={item} value={item}>
                        {HOOK_EVENT_LABELS[item]}
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
          htmlFor="hook-description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            id="hook-description"
            rows={2}
            placeholder="说明该 Hook 的触发场景。"
            {...form.register("description")}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="触发规则">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="匹配器"
            htmlFor="hook-matcher"
            hint="仅对工具事件生效，按工具名前缀匹配，结尾 * 表示通配；留空表示全部。"
            error={form.formState.errors.matcher?.message}
          >
            <Input
              id="hook-matcher"
              placeholder="mcp."
              className="font-mono"
              {...form.register("matcher")}
            />
          </FormField>
          <FormField
            label="超时时间（秒）"
            htmlFor="hook-timeout"
            hint="允许范围 1–600 秒。"
            error={form.formState.errors.timeoutSeconds?.message}
          >
            <Input
              id="hook-timeout"
              type="number"
              min={1}
              max={600}
              {...form.register("timeoutSeconds")}
            />
          </FormField>
        </div>
        <FormField
          label="命令"
          htmlFor="hook-command"
          hint="事件触发时随 Hook 事件下发的命令配置。"
          error={form.formState.errors.command?.message}
        >
          <Textarea
            id="hook-command"
            rows={4}
            placeholder="/usr/local/bin/audit-tool.sh"
            className="font-mono"
            {...form.register("command")}
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
