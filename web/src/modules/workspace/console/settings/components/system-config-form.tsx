"use client";

import { useId } from "react";
import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { z } from "zod";

import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";

import {
  fetchSystemConfigGroup,
  resolveParameterSettingError,
  updateSystemConfigGroup,
} from "../lib/parameter-api";
import { parseParameterJson } from "../lib/parameter-fields";
import { SettingsFormShell } from "./settings-form-shell";

type SystemConfigFormProps = {
  group: string;
};

type SystemConfigValues = {
  payload: string;
};

const systemConfigSchema = z.object({
  payload: z.string().superRefine((value, context) => {
    const text = value.trim();
    if (!text) {
      context.addIssue({ code: "custom", message: "请输入配置 JSON。" });
      return;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(text) as unknown;
    } catch {
      context.addIssue({ code: "custom", message: "配置必须是有效的 JSON。" });
      return;
    }
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      context.addIssue({ code: "custom", message: "配置必须是 JSON 对象。" });
    }
  }),
});

export function SystemConfigForm({ group }: SystemConfigFormProps) {
  const query = useQuery({
    queryKey: ["system-config", group],
    queryFn: () => fetchSystemConfigGroup(group),
    retry: false,
  });

  if (query.isPending) {
    return (
      <ConsoleBlockSkeleton rows={8} className="max-w-2xl space-y-3" itemClassName="h-9 w-full" />
    );
  }

  if (query.isError) {
    return (
      <ConsoleState
        variant="error"
        title="无法加载系统配置"
        description={resolveParameterSettingError(query.error)}
        onRetry={() => void query.refetch()}
      />
    );
  }

  return <SystemConfigEditor group={group} data={query.data ?? {}} />;
}

type SystemConfigEditorProps = {
  group: string;
  data: Record<string, unknown>;
};

function SystemConfigEditor({ group, data }: SystemConfigEditorProps) {
  const fieldId = useId();
  const queryClient = useQueryClient();
  const form = useForm<SystemConfigValues>({
    resolver: zodResolver(systemConfigSchema) as Resolver<SystemConfigValues>,
    defaultValues: {
      payload: JSON.stringify(data, null, 2),
    },
  });

  const mutation = useMutation({
    mutationFn: (body: Record<string, unknown>) => updateSystemConfigGroup(group, body),
    onSuccess: async ({ restartRequired }, body) => {
      form.reset({ payload: JSON.stringify(body, null, 2) });
      await queryClient.invalidateQueries({ queryKey: ["system-config", group] });
      toast.success("系统配置已保存。");
      if (restartRequired) {
        toast.info("部分安全参数需要重启后生效。");
      }
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => {
      const body = parseParameterJson<Record<string, unknown>>(values.payload);
      if (!body) {
        toast.error("配置必须是有效的 JSON 对象。");
        return;
      }
      mutation.mutate(body);
    },
    () => toast.error("请检查配置 JSON。"),
  );

  const message = form.formState.errors.payload?.message;

  return (
    <SettingsFormShell form={form} onSubmit={onSubmit} isPending={mutation.isPending}>
      <div className="space-y-2">
        <div className="flex items-center justify-between gap-2">
          <Label htmlFor={fieldId}>配置 JSON</Label>
          <span className="font-mono text-[0.6875rem] text-muted-foreground">
            /systemconfigs/{group}
          </span>
        </div>
        <Textarea
          id={fieldId}
          rows={18}
          className="font-mono text-xs"
          autoComplete="off"
          spellCheck={false}
          aria-invalid={Boolean(message)}
          {...form.register("payload")}
        />
        <p className="text-xs leading-5 text-muted-foreground">
          该页直接读写 /systemconfigs 的原始分组，不会套用参数设置页的字段约束；敏感字段以 ********
          返回，保持原样保存即可沿用已保存的密钥。
        </p>
        {typeof message === "string" ? <p className="text-xs text-destructive">{message}</p> : null}
      </div>
    </SettingsFormShell>
  );
}
