import { useState } from "react";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, RefreshCw, RotateCcw, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTablePagination, LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import {
  type DocumentStatus,
  type KnowledgeBaseDocument,
  deleteKnowledgeDocument,
  fetchKnowledgeBaseDocuments,
  resolveKnowledgeBaseApiError,
  retryKnowledgeDocument,
} from "../lib/api";
import { DOCUMENT_STATUS_OPTIONS, documentStatusLabel, formatDocumentSize } from "../lib/model";
import { DocumentUploadDialog } from "./document-upload-dialog";
import { KnowledgeErrorState, KnowledgeTableSkeleton } from "./knowledge-states";

type DocumentPanelProps = {
  knowledgeBaseName: string;
  canUpload: boolean;
  canDelete: boolean;
};

const DOCUMENT_COLUMNS = 7;

export function DocumentPanel({ knowledgeBaseName, canUpload, canDelete }: DocumentPanelProps) {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [status, setStatus] = useState<DocumentStatus | "">("");
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeBaseDocument | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);

  const documentsQuery = useQuery({
    queryKey: ["knowledge-base-documents", knowledgeBaseName, page, size, status],
    queryFn: () => fetchKnowledgeBaseDocuments(knowledgeBaseName, page, size, status || undefined),
    placeholderData: keepPreviousData,
    retry: false,
  });

  const deleteMutation = useMutation({
    mutationFn: (document: KnowledgeBaseDocument) =>
      deleteKnowledgeDocument(knowledgeBaseName, document.id),
    onSuccess: async () => {
      if ((documentsQuery.data?.items.length ?? 0) === 1 && page > 1) {
        setPage(page - 1);
      }
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-documents"] });
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-document-count"] });
      setDeleteTarget(null);
      toast.success("文档已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveKnowledgeBaseApiError(error));
    },
  });

  const retryMutation = useMutation({
    mutationFn: (document: KnowledgeBaseDocument) =>
      retryKnowledgeDocument(knowledgeBaseName, document.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-documents"] });
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-document-count"] });
      toast.success("文档已重新解析。");
    },
    onError: (error) => toast.error(resolveKnowledgeBaseApiError(error)),
  });

  const documents = documentsQuery.data?.items ?? [];
  const total = documentsQuery.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / size));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Select
            value={status || "ALL"}
            onValueChange={(value) => {
              setStatus(value === "ALL" ? "" : (value as DocumentStatus));
              setPage(1);
            }}
          >
            <SelectTrigger className="w-32" aria-label="按文档状态筛选">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {DOCUMENT_STATUS_OPTIONS.map((option) => (
                <SelectItem key={option.value || "ALL"} value={option.value || "ALL"}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            type="button"
            variant="outline"
            size="icon"
            onClick={() => void documentsQuery.refetch()}
            aria-label="刷新文档"
            disabled={documentsQuery.isFetching}
          >
            {documentsQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
          </Button>
          <p className="text-xs text-muted-foreground">共 {total} 个文档</p>
        </div>
        {canUpload ? (
          <Button type="button" onClick={() => setUploadOpen(true)}>
            <Upload />
            上传文档
          </Button>
        ) : null}
      </div>

      {documentsQuery.isError ? (
        <KnowledgeErrorState
          title="无法加载文档"
          message={resolveKnowledgeBaseApiError(documentsQuery.error)}
          onRetry={() => void documentsQuery.refetch()}
          className="border-dashed bg-transparent"
        />
      ) : (
        <div className="overflow-hidden border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-4">文档</TableHead>
                <TableHead className="px-4">状态</TableHead>
                <TableHead className="px-4">分块</TableHead>
                <TableHead className="px-4">Token</TableHead>
                <TableHead className="px-4">上传人</TableHead>
                <TableHead className="px-4">上传时间</TableHead>
                <TableHead className="px-4 text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {documentsQuery.isPending ? (
                <KnowledgeTableSkeleton columns={DOCUMENT_COLUMNS} rows={4} />
              ) : documents.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={DOCUMENT_COLUMNS}>
                    <div className="flex flex-col items-center gap-3 py-6">
                      <ConsoleEmpty>该知识库还没有文档。</ConsoleEmpty>
                      {canUpload ? (
                        <Button type="button" variant="outline" onClick={() => setUploadOpen(true)}>
                          <Upload />
                          上传第一个文档
                        </Button>
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                documents.map((document) => {
                  const retrying =
                    retryMutation.isPending && retryMutation.variables?.id === document.id;
                  return (
                    <TableRow key={document.id}>
                      <TableCell className="px-4 py-3">
                        <LongText className="max-w-64 text-sm font-medium">
                          {document.fileName}
                        </LongText>
                        <p className="mt-0.5 text-[11px] text-muted-foreground">
                          {document.mimeType || "未知类型"} ·{" "}
                          {formatDocumentSize(document.fileSize)}
                        </p>
                      </TableCell>
                      <TableCell className="px-4 py-3">
                        <DocumentStatusBadge status={document.status} />
                        {document.errorMsg ? (
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <p className="mt-1 max-w-48 cursor-help truncate text-[11px] text-destructive">
                                {document.errorMsg}
                              </p>
                            </TooltipTrigger>
                            <TooltipContent className="max-w-md whitespace-pre-wrap">
                              {document.errorMsg}
                            </TooltipContent>
                          </Tooltip>
                        ) : null}
                      </TableCell>
                      <TableCell className="px-4 py-3 tabular-nums">
                        {document.chunkCount}
                      </TableCell>
                      <TableCell className="px-4 py-3 tabular-nums">
                        {document.tokenCount}
                      </TableCell>
                      <TableCell className="px-4 py-3 font-mono text-xs text-muted-foreground">
                        {document.uploadedBy || "—"}
                      </TableCell>
                      <TableCell className="px-4 py-3 text-xs text-muted-foreground">
                        {formatUserDateTime(document.createdAt)}
                      </TableCell>
                      <TableCell className="px-4 py-3">
                        <div className="flex items-center justify-end gap-1">
                          {document.status === "FAILED" && canUpload ? (
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`重新解析 ${document.fileName}`}
                              disabled={retrying}
                              onClick={() => retryMutation.mutate(document)}
                            >
                              {retrying ? <Loader2 className="animate-spin" /> : <RotateCcw />}
                            </Button>
                          ) : null}
                          {canDelete ? (
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`删除 ${document.fileName}`}
                              onClick={() => setDeleteTarget(document)}
                            >
                              <Trash2 />
                            </Button>
                          ) : null}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {!documentsQuery.isError && total > 0 ? (
        <DataTablePagination
          currentPage={page}
          pageCount={pageCount}
          pageSize={size}
          onPageChange={setPage}
          onPageSizeChange={(value) => {
            setSize(value);
            setPage(1);
          }}
          disabled={documentsQuery.isFetching}
        />
      ) : null}

      {uploadOpen ? (
        <DocumentUploadDialog
          knowledgeBaseName={knowledgeBaseName}
          onClose={() => setUploadOpen(false)}
          onUploaded={() => {
            if (page !== 1) {
              setPage(1);
            }
          }}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除文档"
        desc={
          deleteTarget
            ? `确定删除「${deleteTarget.fileName}」吗？文档的分块与向量索引会一并删除。`
            : ""
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? "删除中…" : "删除"}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </div>
  );
}

function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  const variant =
    status === "READY"
      ? "secondary"
      : status === "FAILED"
        ? "destructive"
        : status === "PENDING"
          ? "outline"
          : "outline";
  return (
    <Badge variant={variant} className={status === "PENDING" ? "animate-pulse" : undefined}>
      {documentStatusLabel(status)}
    </Badge>
  );
}
