import { CAPABILITY_LABELS, modelTypeLabel } from "@/modules/workspace/ai/market/lib/model";
import type { AiModelCapability, AiModelType } from "@/modules/workspace/ai/providers/lib/api";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import type { ChatArtifact } from "@/modules/workspace/index/lib/chat-api";

import type { AiGenerationItem, AiGenerationOutput, AiGenerationUsage } from "./api";

export type GenerationBadgeVariant = "default" | "secondary" | "destructive" | "outline";

const OUTPUT_TYPE_LABELS: Record<string, string> = {
  text: "文本",
  image: "图片",
  audio: "音频",
  video: "视频",
  file: "文件",
  three_d: "3D",
  vector: "向量",
  json: "JSON",
};

const ARTIFACT_NAME_LABELS: Record<string, string> = {
  image: "生成图片",
  video: "生成视频",
  audio: "生成音频",
  file: "生成文件",
  three_d: "生成 3D 内容",
};

const ARTIFACT_OUTPUT_TYPES = new Set(["image", "video", "audio", "file", "three_d"]);

export function generationCapabilityLabel(capability: string | null | undefined): string {
  if (!capability) {
    return "—";
  }
  return CAPABILITY_LABELS[capability as AiModelCapability] ?? capability;
}

export function generationModelTypeLabel(modelType: string | null | undefined): string {
  if (!modelType) {
    return "—";
  }
  return modelTypeLabel(modelType as AiModelType);
}

export function generationStatusVariant(
  status: AiGenerationItem["status"],
): GenerationBadgeVariant {
  if (status === "FAILED") {
    return "destructive";
  }
  if (status === "RUNNING") {
    return "outline";
  }
  return "secondary";
}

export function generationUnitsLabel(units: number | null | undefined): string {
  return formatTokenCount(units ?? undefined);
}

export function generationUsageEntries(
  usage: AiGenerationUsage,
): { label: string; value: string }[] {
  const entries: { label: string; value: string }[] = [];
  if (usage.inputTokens != null) {
    entries.push({ label: "输入 Token", value: formatTokenCount(usage.inputTokens) });
  }
  if (usage.outputTokens != null) {
    entries.push({ label: "输出 Token", value: formatTokenCount(usage.outputTokens) });
  }
  if (usage.cacheReadTokens != null) {
    entries.push({ label: "缓存读取", value: formatTokenCount(usage.cacheReadTokens) });
  }
  if (usage.cacheWriteTokens != null) {
    entries.push({ label: "缓存写入", value: formatTokenCount(usage.cacheWriteTokens) });
  }
  if (usage.imageCount != null) {
    entries.push({ label: "图片数量", value: formatNumber(usage.imageCount) });
  }
  if (usage.videoSeconds != null) {
    entries.push({ label: "视频时长", value: `${formatNumber(usage.videoSeconds)} 秒` });
  }
  if (usage.audioSeconds != null) {
    entries.push({ label: "音频时长", value: `${formatNumber(usage.audioSeconds)} 秒` });
  }
  if (usage.speechCharacters != null) {
    entries.push({ label: "语音字符", value: formatNumber(usage.speechCharacters) });
  }
  if (usage.taskCount != null) {
    entries.push({ label: "任务数量", value: formatNumber(usage.taskCount) });
  }
  return entries;
}

export function generationOutputText(output: AiGenerationOutput): string | null {
  if (output.type?.toLowerCase() !== "text") {
    return null;
  }
  return output.text ?? "";
}

export function generationOutputArtifact(
  output: AiGenerationOutput,
  index: number,
): ChatArtifact | null {
  const type = output.type?.toLowerCase() ?? "";
  if (!ARTIFACT_OUTPUT_TYPES.has(type)) {
    return null;
  }
  const url = output.url || outputDataUrl(output);
  if (!url && !output.fileId) {
    return null;
  }
  return {
    kind: type.toUpperCase(),
    url: url ?? undefined,
    fileId: output.fileId ?? undefined,
    name: output.filename || `${ARTIFACT_NAME_LABELS[type] ?? "生成内容"} ${index + 1}`,
    mimeType: output.mediaType ?? undefined,
  };
}

export function generationOutputJson(output: AiGenerationOutput): string | null {
  if (output.type?.toLowerCase() !== "json" || output.value === undefined) {
    return null;
  }
  try {
    return JSON.stringify(output.value, null, 2) ?? "null";
  } catch {
    return null;
  }
}

export function generationVectorSummary(output: AiGenerationOutput): string {
  const dimensions = output.dimensions ?? output.values?.length;
  return `${output.model || "未命名模型"} · ${dimensions ?? "—"} 维`;
}

export function generationOutputTypeLabel(type: string): string {
  return OUTPUT_TYPE_LABELS[type?.toLowerCase()] ?? type;
}

function outputDataUrl(output: AiGenerationOutput): string | null {
  if (!output.data) {
    return null;
  }
  return `data:${output.mediaType || "application/octet-stream"};base64,${output.data}`;
}
