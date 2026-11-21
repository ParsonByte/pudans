"use client";

import { useQuery } from "@tanstack/react-query";
import { Eye, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatBeijingDateTime } from "@/lib/date";
import { cn } from "@/lib/utils";

import {
  type DocumentStatus,
  type KnowledgeBaseDocument,
  fetchKnowledgeBaseDetail,
  fetchKnowledgeBaseDocument,
  resolveKnowledgeBaseApiError,
} from "../lib/api";
import { documentStatusLabel, formatDocumentSize } from "../lib/model";

type DocumentDetailDialogProps = {
  knowledgeBaseName: string;
  document: KnowledgeBaseDocument | null;
  onOpenChange: (open: boolean) => void;
  onPreview: (document: KnowledgeBaseDocument) => void;
};

export function DocumentDetailDialog({
  knowledgeBaseName,
  document,
  onOpenChange,
  onPreview,
}: DocumentDetailDialogProps) {
  const documentId = document?.id;
  const detailQuery = useQuery({
    queryKey: ["knowledge-base-document", knowledgeBaseName, documentId],
    queryFn: () => {
      if (documentId == null) {
        throw new Error("缺少文档标识。");
      }
      return fetchKnowledgeBaseDocument(knowledgeBaseName, documentId);
    },
    enabled: documentId != null,
    retry: false,
  });
  const knowledgeBaseQuery = useQuery({
    queryKey: ["knowledge-base", knowledgeBaseName],
    queryFn: () => fetchKnowledgeBaseDetail(knowledgeBaseName),
    enabled: Boolean(document),
    retry: false,
  });
  const detail = detailQuery.data ?? document;

  return (
    <Dialog
      open={Boolean(document)}
      onOpenChange={(open) => {
        if (!open) {
          onOpenChange(false);
        }
      }}
    >
      <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>文档详情</DialogTitle>
          <DialogDescription>
            {knowledgeBaseQuery.data?.spec.displayName
              ? `所属知识库：${knowledgeBaseQuery.data.spec.displayName}`
              : "文档的解析、索引与存储元数据。"}
          </DialogDescription>
        </DialogHeader>

        {detailQuery.isError ? (
          <div className="flex flex-wrap items-center justify-between gap-2 border border-destructive/30 bg-destructive/5 p-3">
            <p className="text-xs text-destructive">
              无法刷新文档详情：{resolveKnowledgeBaseApiError(detailQuery.error)}
            </p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void detailQuery.refetch()}
            >
              <RefreshCw />
              重试
            </Button>
          </div>
        ) : null}

        {detail ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <DocumentStatusBadge status={detail.status} />
              <Badge variant="outline">v{detail.version}</Badge>
            </div>

            {detail.errorMsg ? (
              <p className="border border-destructive/30 bg-destructive/5 p-3 text-xs whitespace-pre-wrap text-destructive">
                {detail.errorMsg}
              </p>
            ) : null}

            <dl className="grid gap-4 sm:grid-cols-2">
              <DetailItem label="文件名" value={detail.fileName} />
              <DetailItem label="MIME 类型" value={detail.mimeType} />
              <DetailItem label="文件大小" value={formatDocumentSize(detail.fileSize)} />
              <DetailItem label="状态" value={documentStatusLabel(detail.status)} />
              <DetailItem label="分块数量" value={String(detail.chunkCount)} />
              <DetailItem label="Token 数量" value={String(detail.tokenCount)} />
              <DetailItem label="版本" value={String(detail.version)} />
              <DetailItem label="上传人" value={detail.uploadedBy} mono />
              <DetailItem label="上传时间" value={formatBeijingDateTime(detail.createdAt)} />
              <DetailItem label="更新时间" value={formatBeijingDateTime(detail.updatedAt)} />
              <DetailItem
                label="存储文件 ID"
                value={detail.storageFileId}
                mono
                className="sm:col-span-2"
              />
              <DetailItem label="校验和" value={detail.checksum} mono className="sm:col-span-2" />
            </dl>
          </div>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            关闭
          </Button>
          {detail?.storageFileId ? (
            <Button type="button" onClick={() => onPreview(detail)}>
              <Eye />
              预览文件
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  const variant =
    status === "READY" ? "secondary" : status === "FAILED" ? "destructive" : "outline";
  return (
    <Badge variant={variant} className={status === "PENDING" ? "animate-pulse" : undefined}>
      {documentStatusLabel(status)}
    </Badge>
  );
}

function DetailItem({
  label,
  value,
  mono = false,
  className,
}: {
  label: string;
  value?: string | number | null;
  mono?: boolean;
  className?: string;
}) {
  return (
    <div className={cn("min-w-0 space-y-1", className)}>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={cn("text-sm", mono && "font-mono text-xs break-all")}>
        {value == null || value === "" ? "—" : value}
      </dd>
    </div>
  );
}
