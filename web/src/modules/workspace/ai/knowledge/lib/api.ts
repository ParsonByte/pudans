import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { resolveApiError } from "@/lib/api-error";
import { AI_API_PREFIX, type AiMetadata } from "@/modules/workspace/ai/providers/lib/api";

export const KNOWLEDGE_BASE_KIND = "KnowledgeBase";

export const KNOWLEDGE_DOCUMENT_MAX_BYTES = 25 * 1024 * 1024;

export const KNOWLEDGE_DOCUMENT_ACCEPT =
  ".txt,.md,.markdown,text/plain,text/markdown,text/x-markdown";

export const QUERY_LOG_LIMIT = 200;

export type KnowledgeBaseVisibility = "PRIVATE" | "TEAM" | "COMPANY";

export type KnowledgeBaseChunkStrategy = "RECURSIVE" | "SEMANTIC" | "FIXED";

export type KnowledgeBaseStatus = "ENABLED" | "DISABLED";

export type DocumentStatus = "PENDING" | "READY" | "FAILED" | "DISABLED";

type KnowledgeBaseAclEntry = {
  subjectType?: "USER" | "ROLE" | "DEPT";
  subjectId?: string;
  permission?: "READ" | "WRITE" | "ADMIN";
};

export type KnowledgeBaseSpec = {
  displayName: string;
  description?: string;
  visibility?: KnowledgeBaseVisibility;
  ownerUserId?: string;
  ownerDeptId?: string;
  acl?: KnowledgeBaseAclEntry[];
  embeddingModel: string;
  chunkStrategy?: KnowledgeBaseChunkStrategy;
  chunkSize?: number;
  chunkOverlap?: number;
  topK?: number;
  scoreThreshold?: number;
  charBudget?: number;
  rerankEnabled?: boolean;
  rerankModel?: string;
  status?: KnowledgeBaseStatus;
  [key: string]: unknown;
};

export type KnowledgeBaseItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: KnowledgeBaseSpec;
};

export type KnowledgeBaseDocument = {
  id: number;
  kbName: string;
  storageFileId: string;
  fileName: string;
  mimeType?: string;
  fileSize?: number;
  checksum?: string;
  status: DocumentStatus;
  chunkCount: number;
  tokenCount: number;
  errorMsg?: string;
  version: number;
  uploadedBy?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type PageResult<T> = {
  page: number;
  size: number;
  total: number;
  items: T[];
};

export type IngestionResult = {
  document: KnowledgeBaseDocument;
  deduplicated: boolean;
};

type Citation = {
  index: number;
  docId: number;
  fileName: string;
  pageNo?: number | null;
  headingPath?: string | null;
  score: number;
  snippet: string;
};

export type KnowledgeBaseRetrievalResult = {
  prompt: string;
  citations: Citation[];
};

export type KnowledgeBaseQueryLog = {
  id: number;
  userId: string;
  requestId?: string;
  kbNames: string[];
  query: string;
  hitChunkIds: number[];
  hitCount: number;
  topScore?: number | string | null;
  rerankUsed: boolean;
  latencyMs?: number | null;
  createdAt?: string;
};

export type KnowledgeBaseSearchRequest = {
  kbCode: string;
  q: string;
  topK: number;
  scoreThreshold: number;
  rrfK: number;
  charBudget: number;
};

export type ReindexResult = {
  queued: number;
};

type ListResponse<T> = {
  items?: T[];
  total?: number;
};

export async function fetchKnowledgeBases(): Promise<KnowledgeBaseItem[]> {
  const response = await axiosInstance.get<ListResponse<KnowledgeBaseItem>>(
    `${AI_API_PREFIX}/knowledgebases`,
  );
  return response.data.items ?? [];
}

export async function createKnowledgeBase(item: KnowledgeBaseItem): Promise<KnowledgeBaseItem> {
  const response = await axiosInstance.post<KnowledgeBaseItem>(
    `${AI_API_PREFIX}/knowledgebases`,
    item,
  );
  return response.data;
}

export async function updateKnowledgeBase(item: KnowledgeBaseItem): Promise<KnowledgeBaseItem> {
  const response = await axiosInstance.put<KnowledgeBaseItem>(
    `${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(item.metadata.name)}`,
    item,
  );
  return response.data;
}

export async function deleteKnowledgeBase(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(name)}`);
}

export async function fetchKnowledgeBaseDocuments(
  name: string,
  page: number,
  size: number,
  status?: DocumentStatus,
): Promise<PageResult<KnowledgeBaseDocument>> {
  const response = await axiosInstance.get<PageResult<KnowledgeBaseDocument>>(
    `${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(name)}/documents`,
    { params: { page, size, ...(status ? { status } : {}) } },
  );
  return {
    page: response.data.page,
    size: response.data.size,
    total: response.data.total,
    items: response.data.items ?? [],
  };
}

export async function fetchKnowledgeBaseDocumentCount(name: string): Promise<number> {
  const result = await fetchKnowledgeBaseDocuments(name, 1, 1);
  return result.total;
}

export async function ingestKnowledgeDocument(
  name: string,
  storageFileId: string,
  fileName: string,
): Promise<IngestionResult> {
  const response = await axiosInstance.post<IngestionResult>(
    `${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(name)}/documents`,
    { storageFileId, fileName },
  );
  return response.data;
}

export async function deleteKnowledgeDocument(name: string, documentId: number): Promise<void> {
  await axiosInstance.delete(
    `${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(name)}/documents/${documentId}`,
  );
}

export async function retryKnowledgeDocument(
  name: string,
  documentId: number,
): Promise<KnowledgeBaseDocument> {
  const response = await axiosInstance.post<KnowledgeBaseDocument>(
    `${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(name)}/documents/${documentId}/retry`,
  );
  return response.data;
}

export async function reindexKnowledgeBase(name: string): Promise<ReindexResult> {
  const response = await axiosInstance.post<ReindexResult>(
    `${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(name)}/reindex`,
  );
  return response.data;
}

export async function searchKnowledgeBase(
  request: KnowledgeBaseSearchRequest,
): Promise<KnowledgeBaseRetrievalResult> {
  const response = await axiosInstance.get<KnowledgeBaseRetrievalResult>(
    `${AI_API_PREFIX}/knowledgebases/-/search`,
    {
      params: {
        q: request.q,
        kbCodes: request.kbCode,
        topK: request.topK,
        scoreThreshold: request.scoreThreshold,
        rrfK: request.rrfK,
        charBudget: request.charBudget,
      },
    },
  );
  return {
    prompt: response.data.prompt ?? "",
    citations: response.data.citations ?? [],
  };
}

export async function fetchKnowledgeBaseQueryLogs(
  name: string,
  limit = QUERY_LOG_LIMIT,
): Promise<KnowledgeBaseQueryLog[]> {
  const response = await axiosInstance.get<KnowledgeBaseQueryLog[]>(
    `${AI_API_PREFIX}/knowledgebases/${encodeURIComponent(name)}/querylogs`,
    { params: { limit } },
  );
  return response.data ?? [];
}

export function resolveKnowledgeBaseApiError(error: unknown): string {
  if (isAxiosError<{ detail?: string }>(error)) {
    const detail = error.response?.data?.detail;
    if (detail) {
      return detail;
    }
  }
  return resolveApiError(error, {
    notFound: "知识库或文档不存在，可能已被删除。",
    conflict: "操作冲突，请刷新后重试。",
  });
}
