import type { UseFormReturn } from "react-hook-form";

import { z } from "zod";

import { CHAT_MODE_TIER_VALUES } from "@/modules/workspace/ai/chat-modes/lib/model";
import type { AiProviderProtocol } from "@/modules/workspace/ai/providers/lib/api";

import { type AiSettingRecord, type AiSettingSpec, type AiSettingTier } from "./api";

const integerText = (message: string, min = 0) =>
  z
    .string()
    .trim()
    .refine((value) => /^\d+$/.test(value) && Number(value) >= min, message);

const decimalText = (message: string, min: number, max: number) =>
  z
    .string()
    .trim()
    .refine((value) => {
      if (!/^\d+(\.\d+)?$/.test(value)) {
        return false;
      }
      const numeric = Number(value);
      return numeric >= min && numeric <= max;
    }, message);

const retryStatusesText = z
  .string()
  .trim()
  .refine((value) => {
    if (value === "") {
      return true;
    }
    return value
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean)
      .every((part) => /^\d{3}$/.test(part) && Number(part) >= 100 && Number(part) <= 599);
  }, "请输入 100–599 之间的状态码，用逗号分隔。");

export const settingsSchema = z.object({
  defaultRouteGroup: z.string(),
  defaultChatMode: z.string(),
  defaultKnowledgeBaseCodes: z.array(z.string()),
  defaults: z.object({
    team: z.string().trim(),
    agent: z.string().trim(),
  }),
  gateway: z.object({
    injectionEnabled: z.boolean(),
    memoryBridgeEnabled: z.boolean(),
    streamIdleTimeoutSeconds: integerText("流空闲超时必须是正整数。", 1),
    rateLimitEnabled: z.boolean(),
    maxRetries: integerText("最大重试必须是非负整数。"),
    retryBackoffMs: integerText("重试退避必须是非负整数。"),
    retryOnStatuses: retryStatusesText,
    requestTimeoutSeconds: integerText("请求超时必须是正整数。", 1),
    supportedProtocols: z.array(z.string()),
  }),
  embedding: z.object({
    dimension: integerText("向量维度必须是正整数。", 1),
    memoryModel: z.string().trim(),
    knowledgeBaseModel: z.string().trim(),
    queryModel: z.string().trim(),
    batchSize: integerText("批处理大小必须是正整数。", 1),
  }),
  retrieval: z.object({
    strategy: z.string().trim().min(1, "请输入检索策略。"),
    rrfK: integerText("RRF K 必须是正整数。", 1),
    l1Limit: integerText("一级召回上限必须是正整数。", 1),
    l2Limit: integerText("二级召回上限必须是非负整数。"),
    orgLimit: integerText("组织召回上限必须是非负整数。"),
    scoreThreshold: decimalText("分数阈值必须在 0 到 1 之间。", 0, 1),
    agenticEnabled: z.boolean(),
  }),
  prompt: z.object({
    maxInputTokens: integerText("最大输入 Token 必须是正整数。", 1),
    maxOutputTokens: integerText("最大输出 Token 必须是正整数。", 1),
    historyWindowTurns: integerText("历史窗口轮数必须是非负整数。"),
    knowledgeBaseCharBudget: integerText("知识库字符预算必须是非负整数。"),
  }),
  routing: z.object({
    classifierEnabled: z.boolean(),
    classifierFallbackTier: z.enum(CHAT_MODE_TIER_VALUES),
  }),
  memory: z.object({
    enabled: z.boolean(),
    extractionEnabled: z.boolean(),
    extractionRouteGroup: z.string(),
    maxMessagesPerExtraction: integerText("单次提取消息数必须是正整数。", 1),
    maxBackgroundMessages: integerText("后台消息数必须是非负整数。"),
    maxMemoriesPerSession: integerText("单会话记忆上限必须是正整数。", 1),
    dedupEnabled: z.boolean(),
    conflictRecallTopK: integerText("冲突召回 Top K 必须是正整数。", 1),
    memoryLimit: integerText("记忆总量限制必须是非负整数。"),
    creditLimit: integerText("点数限制必须是非负整数。"),
    promptEnabled: z.boolean(),
    generationLogEnabled: z.boolean(),
    l2Enabled: z.boolean(),
    l3Enabled: z.boolean(),
    jevMinConfidence: decimalText("最小置信度必须在 0 到 1 之间。", 0, 1),
    l3RefreshHours: integerText("L3 刷新间隔必须是正整数。", 1),
    decayEnabled: z.boolean(),
    decayMinScore: integerText("衰减最低分必须是非负整数。"),
    decayAccessBoost: integerText("访问加成必须是非负整数。"),
    decayDailyAmount: integerText("每日衰减量必须是非负整数。"),
    decayStaleDays: integerText("陈旧天数必须是非负整数。"),
    deleteRetentionDays: integerText("删除保留天数必须是非负整数。"),
    requestMinReasonLength: integerText("申请理由最小长度必须是非负整数。"),
    orgPromotionThreshold: integerText("组织提升阈值必须是正整数。", 1),
    requireDoubleApprovalForL0: z.boolean(),
    excludeSessionPrefixes: z.string(),
  }),
});

export type SettingsFormValues = z.infer<typeof settingsSchema>;

export type SettingsFormApi = UseFormReturn<SettingsFormValues>;

function numberText(value: number | undefined): string {
  return value == null ? "" : String(value);
}

export function toFormValues(setting?: AiSettingRecord): SettingsFormValues {
  const spec = setting?.spec;
  const gateway = spec?.gateway;
  const embedding = spec?.embedding;
  const retrieval = spec?.retrieval;
  const prompt = spec?.prompt;
  const routing = spec?.routing;
  const memory = spec?.memory;
  return {
    defaultRouteGroup: spec?.defaultRouteGroup ?? "",
    defaultChatMode: spec?.defaultChatMode ?? "",
    defaultKnowledgeBaseCodes: [...(spec?.defaultKnowledgeBaseCodes ?? [])],
    defaults: {
      team: spec?.defaults?.team ?? "",
      agent: spec?.defaults?.agent ?? "",
    },
    gateway: {
      injectionEnabled: gateway?.injectionEnabled ?? false,
      memoryBridgeEnabled: gateway?.memoryBridgeEnabled ?? false,
      streamIdleTimeoutSeconds: numberText(gateway?.streamIdleTimeoutSeconds),
      rateLimitEnabled: gateway?.rateLimitEnabled ?? false,
      maxRetries: numberText(gateway?.maxRetries),
      retryBackoffMs: numberText(gateway?.retryBackoffMs),
      retryOnStatuses: (gateway?.retryOnStatuses ?? []).join(", "),
      requestTimeoutSeconds: numberText(gateway?.requestTimeoutSeconds),
      supportedProtocols: [...(gateway?.supportedProtocols ?? [])],
    },
    embedding: {
      dimension: numberText(embedding?.dimension),
      memoryModel: embedding?.memoryModel ?? "",
      knowledgeBaseModel: embedding?.knowledgeBaseModel ?? "",
      queryModel: embedding?.queryModel ?? "",
      batchSize: numberText(embedding?.batchSize),
    },
    retrieval: {
      strategy: retrieval?.strategy ?? "",
      rrfK: numberText(retrieval?.rrfK),
      l1Limit: numberText(retrieval?.l1Limit),
      l2Limit: numberText(retrieval?.l2Limit),
      orgLimit: numberText(retrieval?.orgLimit),
      scoreThreshold: numberText(retrieval?.scoreThreshold),
      agenticEnabled: retrieval?.agenticEnabled ?? false,
    },
    prompt: {
      maxInputTokens: numberText(prompt?.maxInputTokens),
      maxOutputTokens: numberText(prompt?.maxOutputTokens),
      historyWindowTurns: numberText(prompt?.historyWindowTurns),
      knowledgeBaseCharBudget: numberText(prompt?.knowledgeBaseCharBudget),
    },
    routing: {
      classifierEnabled: routing?.classifierEnabled ?? false,
      classifierFallbackTier: routing?.classifierFallbackTier ?? "HIGH",
    },
    memory: {
      enabled: memory?.enabled ?? false,
      extractionEnabled: memory?.extractionEnabled ?? false,
      extractionRouteGroup: memory?.extractionRouteGroup ?? "",
      maxMessagesPerExtraction: numberText(memory?.maxMessagesPerExtraction),
      maxBackgroundMessages: numberText(memory?.maxBackgroundMessages),
      maxMemoriesPerSession: numberText(memory?.maxMemoriesPerSession),
      dedupEnabled: memory?.dedupEnabled ?? false,
      conflictRecallTopK: numberText(memory?.conflictRecallTopK),
      memoryLimit: numberText(memory?.memoryLimit),
      creditLimit: numberText(memory?.creditLimit),
      promptEnabled: memory?.promptEnabled ?? false,
      generationLogEnabled: memory?.generationLogEnabled ?? false,
      l2Enabled: memory?.l2Enabled ?? false,
      l3Enabled: memory?.l3Enabled ?? false,
      jevMinConfidence: numberText(memory?.jevMinConfidence),
      l3RefreshHours: numberText(memory?.l3RefreshHours),
      decayEnabled: memory?.decayEnabled ?? false,
      decayMinScore: numberText(memory?.decayMinScore),
      decayAccessBoost: numberText(memory?.decayAccessBoost),
      decayDailyAmount: numberText(memory?.decayDailyAmount),
      decayStaleDays: numberText(memory?.decayStaleDays),
      deleteRetentionDays: numberText(memory?.deleteRetentionDays),
      requestMinReasonLength: numberText(memory?.requestMinReasonLength),
      orgPromotionThreshold: numberText(memory?.orgPromotionThreshold),
      requireDoubleApprovalForL0: memory?.requireDoubleApprovalForL0 ?? false,
      excludeSessionPrefixes: (memory?.excludeSessionPrefixes ?? []).join("\n"),
    },
  };
}

function parseRetryStatuses(value: string): number[] {
  return [
    ...new Set(
      value
        .split(",")
        .map((part) => Number(part.trim()))
        .filter(Boolean),
    ),
  ].sort((left, right) => left - right);
}

function parseLines(value: string): string[] {
  return value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

function blankToUndefined(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

export function toSettingSpec(values: SettingsFormValues, setting: AiSettingRecord): AiSettingSpec {
  const spec = setting.spec;
  return {
    ...spec,
    defaultRouteGroup: blankToUndefined(values.defaultRouteGroup),
    defaultChatMode: blankToUndefined(values.defaultChatMode),
    defaultKnowledgeBaseCodes: values.defaultKnowledgeBaseCodes,
    defaults: {
      ...(spec.defaults ?? {}),
      team: values.defaults.team.trim(),
      agent: values.defaults.agent.trim(),
    },
    gateway: {
      ...(spec.gateway ?? {}),
      injectionEnabled: values.gateway.injectionEnabled,
      memoryBridgeEnabled: values.gateway.memoryBridgeEnabled,
      streamIdleTimeoutSeconds: Number(values.gateway.streamIdleTimeoutSeconds),
      rateLimitEnabled: values.gateway.rateLimitEnabled,
      maxRetries: Number(values.gateway.maxRetries),
      retryBackoffMs: Number(values.gateway.retryBackoffMs),
      retryOnStatuses: parseRetryStatuses(values.gateway.retryOnStatuses),
      requestTimeoutSeconds: Number(values.gateway.requestTimeoutSeconds),
      supportedProtocols: values.gateway.supportedProtocols as AiProviderProtocol[],
    },
    embedding: {
      ...(spec.embedding ?? {}),
      dimension: Number(values.embedding.dimension),
      memoryModel: blankToUndefined(values.embedding.memoryModel),
      knowledgeBaseModel: blankToUndefined(values.embedding.knowledgeBaseModel),
      queryModel: blankToUndefined(values.embedding.queryModel),
      batchSize: Number(values.embedding.batchSize),
    },
    retrieval: {
      ...(spec.retrieval ?? {}),
      strategy: values.retrieval.strategy.trim(),
      rrfK: Number(values.retrieval.rrfK),
      l1Limit: Number(values.retrieval.l1Limit),
      l2Limit: Number(values.retrieval.l2Limit),
      orgLimit: Number(values.retrieval.orgLimit),
      scoreThreshold: Number(values.retrieval.scoreThreshold),
      agenticEnabled: values.retrieval.agenticEnabled,
    },
    prompt: {
      ...(spec.prompt ?? {}),
      maxInputTokens: Number(values.prompt.maxInputTokens),
      maxOutputTokens: Number(values.prompt.maxOutputTokens),
      historyWindowTurns: Number(values.prompt.historyWindowTurns),
      knowledgeBaseCharBudget: Number(values.prompt.knowledgeBaseCharBudget),
    },
    routing: {
      ...(spec.routing ?? {}),
      classifierEnabled: values.routing.classifierEnabled,
      classifierFallbackTier: values.routing.classifierFallbackTier as AiSettingTier,
    },
    memory: {
      ...(spec.memory ?? {}),
      enabled: values.memory.enabled,
      extractionEnabled: values.memory.extractionEnabled,
      extractionRouteGroup: blankToUndefined(values.memory.extractionRouteGroup),
      maxMessagesPerExtraction: Number(values.memory.maxMessagesPerExtraction),
      maxBackgroundMessages: Number(values.memory.maxBackgroundMessages),
      maxMemoriesPerSession: Number(values.memory.maxMemoriesPerSession),
      dedupEnabled: values.memory.dedupEnabled,
      conflictRecallTopK: Number(values.memory.conflictRecallTopK),
      memoryLimit: Number(values.memory.memoryLimit),
      creditLimit: Number(values.memory.creditLimit),
      promptEnabled: values.memory.promptEnabled,
      generationLogEnabled: values.memory.generationLogEnabled,
      l2Enabled: values.memory.l2Enabled,
      l3Enabled: values.memory.l3Enabled,
      jevMinConfidence: Number(values.memory.jevMinConfidence),
      l3RefreshHours: Number(values.memory.l3RefreshHours),
      decayEnabled: values.memory.decayEnabled,
      decayMinScore: Number(values.memory.decayMinScore),
      decayAccessBoost: Number(values.memory.decayAccessBoost),
      decayDailyAmount: Number(values.memory.decayDailyAmount),
      decayStaleDays: Number(values.memory.decayStaleDays),
      deleteRetentionDays: Number(values.memory.deleteRetentionDays),
      requestMinReasonLength: Number(values.memory.requestMinReasonLength),
      orgPromotionThreshold: Number(values.memory.orgPromotionThreshold),
      requireDoubleApprovalForL0: values.memory.requireDoubleApprovalForL0,
      excludeSessionPrefixes: parseLines(values.memory.excludeSessionPrefixes),
    },
  };
}
