import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FileUp, Info, Loader2, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatStorageFileSize } from "@/modules/workspace/console/storage-files/lib/model";

import {
  type AiProviderFile,
  deleteProviderFile,
  fetchProviderFile,
  fetchProviderFiles,
  resolveAiProviderApiError,
} from "../lib/api";
import { providerFilePurposeLabel, providerFileTime } from "../lib/model";
import { ProviderFileUploadDialog } from "./provider-file-upload-dialog";

const PROVIDER_FILE_PAGE_SIZE = 100;

type ProviderFilesSectionProps = {
  providerName: string;
  canCreate: boolean;
  canDelete: boolean;
};

export function ProviderFilesSection({
  providerName,
  canCreate,
  canDelete,
}: ProviderFilesSectionProps) {
  const queryClient = useQueryClient();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AiProviderFile | null>(null);
  const [detailTarget, setDetailTarget] = useState<AiProviderFile | null>(null);

  const filesQuery = useQuery({
    queryKey: ["ai-provider-files", providerName],
    queryFn: () =>
      fetchProviderFiles(providerName, {
        limit: PROVIDER_FILE_PAGE_SIZE,
        order: "desc",
      }),
    retry: false,
  });
  const files = filesQuery.data?.data ?? [];
  const hasMoreFiles = filesQuery.data?.hasMore === true;

  const deleteMutation = useMutation({
    mutationFn: (file: AiProviderFile) => deleteProviderFile(providerName, file.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-files", providerName] });
      setDeleteTarget(null);
      toast.success("供应商文件已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveAiProviderApiError(error));
    },
  });

  return (
    <section className="space-y-3 border bg-card/30 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold">文件</h3>
          <p className="text-xs text-muted-foreground">
            {files.length} 个供应商文件{hasMoreFiles ? "（还有更多未加载）" : ""}
            ；上传前需先在平台存储中保存文件。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canCreate ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={filesQuery.isFetching}
              onClick={() => setUploadOpen(true)}
            >
              <FileUp />
              上传文件
            </Button>
          ) : null}
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={filesQuery.isFetching}
            onClick={() => void filesQuery.refetch()}
          >
            {filesQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>
      </div>

      {filesQuery.isPending ? (
        <div className="space-y-2">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-10 w-full" />
          ))}
        </div>
      ) : filesQuery.isError ? (
        <div className="flex flex-wrap items-center justify-between gap-3 border border-destructive/30 bg-destructive/5 px-3 py-2">
          <p className="text-xs text-destructive">{resolveAiProviderApiError(filesQuery.error)}</p>
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => void filesQuery.refetch()}
          >
            重试
          </Button>
        </div>
      ) : files.length === 0 ? (
        <p className="border border-dashed p-4 text-sm text-muted-foreground">
          还没有供应商文件。上传后可在对话或工具调用中引用。
        </p>
      ) : (
        <div className="divide-y border">
          {files.map((file) => (
            <div key={file.id} className="flex flex-wrap items-center gap-3 px-3 py-2">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{file.filename || file.id}</p>
                <p className="truncate font-mono text-[11px] text-muted-foreground">{file.id}</p>
              </div>
              <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
                <Badge variant="outline">{providerFilePurposeLabel(file.purpose)}</Badge>
                <span>{formatStorageFileSize(file.bytes)}</span>
                {providerFileTime(file.createdAt) ? (
                  <span>创建 {providerFileTime(file.createdAt)}</span>
                ) : null}
                {providerFileTime(file.expiresAt) ? (
                  <span>到期 {providerFileTime(file.expiresAt)}</span>
                ) : null}
              </div>
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  aria-label="查看文件详情"
                  title="查看文件详情"
                  onClick={() => setDetailTarget(file)}
                >
                  <Info />
                </Button>
                {canDelete ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    className="text-destructive hover:text-destructive"
                    aria-label="删除供应商文件"
                    title="删除供应商文件"
                    onClick={() => setDeleteTarget(file)}
                  >
                    <Trash2 />
                  </Button>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}

      {uploadOpen ? (
        <ProviderFileUploadDialog
          providerName={providerName}
          onClose={() => setUploadOpen(false)}
        />
      ) : null}

      {detailTarget ? (
        <ProviderFileDetailDialog
          providerName={providerName}
          file={detailTarget}
          onClose={() => setDetailTarget(null)}
        />
      ) : null}

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title={`删除供应商文件 ${deleteTarget?.filename || deleteTarget?.id || ""}`}
        desc="删除后该文件将不再可用，已生成的引用可能失效。"
        confirmText="删除文件"
        destructive
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </section>
  );
}

type ProviderFileDetailDialogProps = {
  providerName: string;
  file: AiProviderFile;
  onClose: () => void;
};

function ProviderFileDetailDialog({ providerName, file, onClose }: ProviderFileDetailDialogProps) {
  const fileQuery = useQuery({
    queryKey: ["ai-provider-file", providerName, file.id],
    queryFn: () => fetchProviderFile(providerName, file.id),
    initialData: file,
    retry: false,
  });
  const current = fileQuery.data ?? file;

  return (
    <DetailDialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={current.filename || current.id}
      description="供应商文件详情"
      size="xl"
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <InfoRow label="文件 ID" value={<span className="font-mono text-xs">{current.id}</span>} />
        <InfoRow
          label="对象"
          value={<span className="font-mono text-xs">{current.object || "—"}</span>}
        />
        <InfoRow label="用途" value={providerFilePurposeLabel(current.purpose)} />
        <InfoRow label="大小" value={formatStorageFileSize(current.bytes)} />
        <InfoRow label="创建时间" value={providerFileTime(current.createdAt) || "—"} />
        <InfoRow label="到期时间" value={providerFileTime(current.expiresAt) || "—"} />
      </div>
      {fileQuery.isError ? (
        <p className="mt-3 text-xs text-destructive">
          {resolveAiProviderApiError(fileQuery.error)}
        </p>
      ) : null}
    </DetailDialog>
  );
}
