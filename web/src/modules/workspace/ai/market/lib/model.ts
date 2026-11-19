import type {
  AiModality,
  AiModelCapability,
  AiModelSpec,
  AiModelType,
} from "../../providers/lib/api";
import { MODEL_TYPE_OPTIONS, optionLabel } from "../../providers/lib/model";

export const CAPABILITY_LABELS: Record<AiModelCapability, string> = {
  CHAT: "文本生成",
  TOOLS: "工具调用",
  JSON: "JSON 输出",
  VISION: "视觉理解",
  AUDIO_UNDERSTANDING: "音频理解",
  VIDEO_UNDERSTANDING: "视频理解",
  EMBEDDING: "文本向量",
  RERANK: "重排",
  IMAGE_GENERATION: "图片生成",
  IMAGE_EDITING: "图片编辑",
  VIDEO_GENERATION: "视频生成",
  SPEECH_RECOGNITION: "语音识别",
  SPEECH_SYNTHESIS: "语音合成",
  AUDIO_GENERATION: "音频生成",
  MUSIC_GENERATION: "音乐生成",
  THREE_D_GENERATION: "3D 生成",
  OCR: "OCR",
  STREAM: "流式输出",
};

export const MODALITY_LABELS: Record<AiModality, string> = {
  TEXT: "文本",
  IMAGE: "图片",
  AUDIO: "音频",
  VIDEO: "视频",
  FILE: "文件",
  THREE_D: "3D 模型",
};

export function modelCapabilities(spec: AiModelSpec): AiModelCapability[] {
  if (spec.capabilities && spec.capabilities.length > 0) {
    return spec.capabilities;
  }
  const derived = new Set<AiModelCapability>();
  switch (spec.modelType) {
    case "CHAT": {
      derived.add("CHAT");
      if (spec.supportsStream !== false) {
        derived.add("STREAM");
      }
      if (spec.supportsTools) {
        derived.add("TOOLS");
      }
      if (spec.supportsJson) {
        derived.add("JSON");
      }
      if (spec.supportsVision) {
        derived.add("VISION");
      }
      break;
    }
    case "EMBEDDING":
      derived.add("EMBEDDING");
      break;
    case "RERANK":
      derived.add("RERANK");
      break;
    case "IMAGE":
      derived.add("IMAGE_GENERATION");
      derived.add("IMAGE_EDITING");
      derived.add("OCR");
      break;
    case "VIDEO":
      derived.add("VIDEO_GENERATION");
      break;
    case "SPEECH":
      derived.add("SPEECH_RECOGNITION");
      derived.add("SPEECH_SYNTHESIS");
      break;
    case "AUDIO":
      derived.add("AUDIO_GENERATION");
      derived.add("AUDIO_UNDERSTANDING");
      break;
    case "MUSIC":
      derived.add("MUSIC_GENERATION");
      break;
    case "THREE_D":
      derived.add("THREE_D_GENERATION");
      break;
  }
  return [...derived];
}

export function modelInputModalities(spec: AiModelSpec): AiModality[] {
  if (spec.inputModalities && spec.inputModalities.length > 0) {
    return spec.inputModalities;
  }
  switch (spec.modelType) {
    case "CHAT":
      return spec.supportsVision ? ["TEXT", "IMAGE"] : ["TEXT"];
    case "EMBEDDING":
    case "RERANK":
      return ["TEXT"];
    case "IMAGE":
    case "VIDEO":
    case "MUSIC":
    case "AUDIO":
    case "THREE_D":
      return ["TEXT", "IMAGE"];
    case "SPEECH":
      return ["AUDIO", "TEXT"];
  }
}

export function modelOutputModalities(spec: AiModelSpec): AiModality[] {
  if (spec.outputModalities && spec.outputModalities.length > 0) {
    return spec.outputModalities;
  }
  switch (spec.modelType) {
    case "IMAGE":
      return ["IMAGE"];
    case "VIDEO":
      return ["VIDEO"];
    case "SPEECH":
      return ["TEXT", "AUDIO"];
    case "AUDIO":
    case "MUSIC":
      return ["AUDIO"];
    case "THREE_D":
      return ["THREE_D"];
    case "CHAT":
    case "EMBEDDING":
    case "RERANK":
      return ["TEXT"];
  }
}

export function modelTypeLabel(type: AiModelType): string {
  return optionLabel(MODEL_TYPE_OPTIONS, type, type);
}

export function modelCapabilityLabels(spec: AiModelSpec): string[] {
  return modelCapabilities(spec).map((capability) => CAPABILITY_LABELS[capability]);
}

export function toggleSetValue<T>(current: ReadonlySet<T>, value: T): Set<T> {
  const next = new Set(current);
  if (next.has(value)) {
    next.delete(value);
  } else {
    next.add(value);
  }
  return next;
}
