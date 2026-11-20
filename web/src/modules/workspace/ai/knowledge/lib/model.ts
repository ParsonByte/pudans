import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

import type {
  DocumentStatus,
  KnowledgeBaseChunkStrategy,
  KnowledgeBaseItem,
  KnowledgeBaseStatus,
  KnowledgeBaseVisibility,
} from "./api";

type Option<T extends string> = {
  value: T;
  label: string;
  disabled?: boolean;
};

export const VISIBILITY_OPTIONS: Option<KnowledgeBaseVisibility>[] = [
  { value: "PRIVATE", label: "私有" },
  { value: "TEAM", label: "团队" },
  { value: "COMPANY", label: "公司" },
];

export const VISIBILITY_HINTS: Record<KnowledgeBaseVisibility, string> = {
  PRIVATE: "仅创建者与 ACL 授权对象可读。",
  TEAM: "按所属部门可读；当前接口不提供部门上下文，建议优先使用公司可见。",
  COMPANY: "所有可访问 AI 工作空间的成员可读。",
};

export const CHUNK_STRATEGY_OPTIONS: Option<KnowledgeBaseChunkStrategy>[] = [
  { value: "RECURSIVE", label: "递归" },
  { value: "FIXED", label: "固定长度" },
];

export const DOCUMENT_STATUS_OPTIONS: Option<DocumentStatus | "">[] = [
  { value: "", label: "全部状态" },
  { value: "PENDING", label: "待解析" },
  { value: "READY", label: "就绪" },
  { value: "FAILED", label: "失败" },
  { value: "DISABLED", label: "已停用" },
];

export const KNOWLEDGE_BASE_STATUS_OPTIONS: Option<KnowledgeBaseStatus | "">[] = [
  { value: "", label: "全部状态" },
  { value: "ENABLED", label: "已启用" },
  { value: "DISABLED", label: "已停用" },
];

const VISIBILITY_LABELS: Record<KnowledgeBaseVisibility, string> = {
  PRIVATE: "私有",
  TEAM: "团队",
  COMPANY: "公司",
};

const CHUNK_STRATEGY_LABELS: Record<KnowledgeBaseChunkStrategy, string> = {
  RECURSIVE: "递归切片",
  FIXED: "固定切片",
  SEMANTIC: "语义切片",
};

const KNOWLEDGE_BASE_STATUS_LABELS: Record<KnowledgeBaseStatus, string> = {
  ENABLED: "已启用",
  DISABLED: "已停用",
};

const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  PENDING: "待解析",
  READY: "就绪",
  FAILED: "失败",
  DISABLED: "已停用",
};

export function visibilityLabel(value: KnowledgeBaseVisibility | undefined): string {
  return value ? VISIBILITY_LABELS[value] : "团队";
}

export function chunkStrategyLabel(value: KnowledgeBaseChunkStrategy | undefined): string {
  return value ? CHUNK_STRATEGY_LABELS[value] : CHUNK_STRATEGY_LABELS.RECURSIVE;
}

export function knowledgeBaseStatusLabel(value: KnowledgeBaseStatus | undefined): string {
  return value ? KNOWLEDGE_BASE_STATUS_LABELS[value] : KNOWLEDGE_BASE_STATUS_LABELS.ENABLED;
}

export function documentStatusLabel(value: DocumentStatus): string {
  return DOCUMENT_STATUS_LABELS[value];
}

export function knowledgeBaseCode(name: string): string {
  const slash = name.lastIndexOf("/");
  return slash < 0 ? name : name.slice(slash + 1);
}

export function generateKnowledgeBaseName(displayName: string): string {
  const slug =
    displayName
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 40) || "kb";
  return `${slug}-${Date.now().toString(36)}`;
}

export function matchesKnowledgeBase(item: KnowledgeBaseItem, keyword: string): boolean {
  if (!keyword) {
    return true;
  }
  const haystack = [
    item.metadata.name,
    item.spec.displayName,
    item.spec.description ?? "",
    item.spec.embeddingModel,
  ]
    .join(" ")
    .toLowerCase();
  return haystack.includes(keyword);
}

export function formatScore(value: number | string | null | undefined): string {
  if (value == null || value === "") {
    return "—";
  }
  const score = typeof value === "number" ? value : Number(value);
  return Number.isFinite(score) ? score.toFixed(3) : "—";
}

export function formatLatency(value: number | null | undefined): string {
  return value == null ? "—" : `${value} ms`;
}

export function formatDocumentSize(bytes: number | undefined): string {
  return bytes && bytes > 0 ? formatFileSize(bytes) : "0 B";
}

export function searchWords(query: string): string[] {
  return query.trim().split(/\s+/).filter(Boolean);
}
