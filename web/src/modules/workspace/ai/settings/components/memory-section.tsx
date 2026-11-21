import { Controller, useWatch } from "react-hook-form";

import { useQuery } from "@tanstack/react-query";

import { FormField } from "@/components/form-field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { routeGroupOptions } from "@/modules/workspace/ai/chat-modes/lib/model";
import { fetchAllRouteGroups } from "@/modules/workspace/ai/providers/lib/api";

import type { SettingsFormApi } from "../lib/schema";
import { NO_SELECTION_VALUE, NumberField, SettingsSection, SwitchField } from "./settings-fields";

type MemorySectionProps = {
  form: SettingsFormApi;
};

export function MemorySection({ form }: MemorySectionProps) {
  const routeGroupsQuery = useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
    retry: false,
  });
  const extractionRouteGroup = useWatch({
    control: form.control,
    name: "memory.extractionRouteGroup",
  });
  const groupOptions = routeGroupOptions(routeGroupsQuery.data ?? [], extractionRouteGroup);

  return (
    <SettingsSection
      title="记忆"
      description="记忆提取、层级、去重、衰减、审批与保留策略；开关与阈值直接影响长期记忆的生成与淘汰。"
    >
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <Controller
          control={form.control}
          name="memory.enabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-enabled"
              label="启用记忆"
              description="关闭后所有记忆读写都会停用。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.extractionEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-extraction"
              label="自动提取"
              description="从对话中提取长期记忆。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.dedupEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-dedup"
              label="去重"
              description="合并语义重复的记忆。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.promptEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-prompt"
              label="提示词注入"
              description="把命中的记忆写入系统提示词。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.generationLogEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-generation-log"
              label="生成日志"
              description="记录记忆提取与生成过程。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.l2Enabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-l2"
              label="L2 层级"
              description="启用组织与团队级记忆。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.l3Enabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-l3"
              label="L3 层级"
              description="启用长期画像与摘要记忆。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.decayEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-decay"
              label="记忆衰减"
              description="按访问频率与时间淘汰低分记忆。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="memory.requireDoubleApprovalForL0"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-memory-double-approval"
              label="L0 双人审批"
              description="L0 级记忆变更需要两次审批。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <NumberField
          id="ai-setting-memory-max-extraction"
          label="单次提取消息数"
          min={1}
          registration={form.register("memory.maxMessagesPerExtraction")}
          error={form.formState.errors.memory?.maxMessagesPerExtraction?.message}
        />
        <NumberField
          id="ai-setting-memory-max-background"
          label="后台消息数"
          min={0}
          registration={form.register("memory.maxBackgroundMessages")}
          error={form.formState.errors.memory?.maxBackgroundMessages?.message}
        />
        <NumberField
          id="ai-setting-memory-max-per-session"
          label="单会话记忆上限"
          min={1}
          registration={form.register("memory.maxMemoriesPerSession")}
          error={form.formState.errors.memory?.maxMemoriesPerSession?.message}
        />
        <NumberField
          id="ai-setting-memory-conflict-top-k"
          label="冲突召回 Top K"
          min={1}
          registration={form.register("memory.conflictRecallTopK")}
          error={form.formState.errors.memory?.conflictRecallTopK?.message}
        />
        <NumberField
          id="ai-setting-memory-limit"
          label="记忆总量限制"
          min={0}
          registration={form.register("memory.memoryLimit")}
          error={form.formState.errors.memory?.memoryLimit?.message}
        />
        <NumberField
          id="ai-setting-memory-credit-limit"
          label="点数限制"
          min={0}
          registration={form.register("memory.creditLimit")}
          error={form.formState.errors.memory?.creditLimit?.message}
        />
        <NumberField
          id="ai-setting-memory-l3-refresh"
          label="L3 刷新间隔"
          hint="单位：小时。"
          min={1}
          registration={form.register("memory.l3RefreshHours")}
          error={form.formState.errors.memory?.l3RefreshHours?.message}
        />
        <NumberField
          id="ai-setting-memory-jev-confidence"
          label="最小置信度"
          hint="0 到 1 之间。"
          min={0}
          step="0.01"
          registration={form.register("memory.jevMinConfidence")}
          error={form.formState.errors.memory?.jevMinConfidence?.message}
        />
        <NumberField
          id="ai-setting-memory-decay-min-score"
          label="衰减最低分"
          min={0}
          registration={form.register("memory.decayMinScore")}
          error={form.formState.errors.memory?.decayMinScore?.message}
        />
        <NumberField
          id="ai-setting-memory-decay-access-boost"
          label="访问加成"
          min={0}
          registration={form.register("memory.decayAccessBoost")}
          error={form.formState.errors.memory?.decayAccessBoost?.message}
        />
        <NumberField
          id="ai-setting-memory-decay-daily"
          label="每日衰减量"
          min={0}
          registration={form.register("memory.decayDailyAmount")}
          error={form.formState.errors.memory?.decayDailyAmount?.message}
        />
        <NumberField
          id="ai-setting-memory-decay-stale-days"
          label="陈旧天数"
          min={0}
          registration={form.register("memory.decayStaleDays")}
          error={form.formState.errors.memory?.decayStaleDays?.message}
        />
        <NumberField
          id="ai-setting-memory-retention-days"
          label="删除保留天数"
          min={0}
          registration={form.register("memory.deleteRetentionDays")}
          error={form.formState.errors.memory?.deleteRetentionDays?.message}
        />
        <NumberField
          id="ai-setting-memory-reason-length"
          label="申请理由最小长度"
          min={0}
          registration={form.register("memory.requestMinReasonLength")}
          error={form.formState.errors.memory?.requestMinReasonLength?.message}
        />
        <NumberField
          id="ai-setting-memory-org-threshold"
          label="组织提升阈值"
          min={1}
          registration={form.register("memory.orgPromotionThreshold")}
          error={form.formState.errors.memory?.orgPromotionThreshold?.message}
        />
      </div>

      <FormField
        label="提取路由组"
        htmlFor="ai-setting-memory-extraction-route-group"
        hint={
          routeGroupsQuery.isError
            ? "无法加载路由组，请检查权限后重试。"
            : routeGroupsQuery.isPending
              ? "正在加载路由组…"
              : "记忆提取任务使用的路由组；留空时跟随默认路由组。"
        }
        error={form.formState.errors.memory?.extractionRouteGroup?.message}
      >
        <Controller
          control={form.control}
          name="memory.extractionRouteGroup"
          render={({ field }) => (
            <Select
              value={field.value || NO_SELECTION_VALUE}
              disabled={routeGroupsQuery.isPending}
              onValueChange={(value) => field.onChange(value === NO_SELECTION_VALUE ? "" : value)}
            >
              <SelectTrigger id="ai-setting-memory-extraction-route-group" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NO_SELECTION_VALUE}>不指定</SelectItem>
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
        label="排除会话前缀"
        htmlFor="ai-setting-memory-exclude-prefixes"
        hint="每行一个前缀；匹配这些前缀的会话不会写入记忆。"
        error={form.formState.errors.memory?.excludeSessionPrefixes?.message}
      >
        <Textarea
          id="ai-setting-memory-exclude-prefixes"
          rows={4}
          placeholder={"internal-\nbenchmark-"}
          className="font-mono text-xs"
          {...form.register("memory.excludeSessionPrefixes")}
        />
      </FormField>
    </SettingsSection>
  );
}
