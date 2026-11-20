import { useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, Upload } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Progress } from "@/components/ui/progress";
import { uploadChatAttachment } from "@/modules/workspace/index/lib/chat-api";

import {
  KNOWLEDGE_DOCUMENT_ACCEPT,
  KNOWLEDGE_DOCUMENT_MAX_BYTES,
  ingestKnowledgeDocument,
  resolveKnowledgeBaseApiError,
} from "../lib/api";
import { formatDocumentSize } from "../lib/model";

type UploadPhase = "idle" | "uploading" | "ingesting";

type DocumentUploadDialogProps = {
  knowledgeBaseName: string;
  onClose: () => void;
  onUploaded: () => void;
};

export function DocumentUploadDialog({
  knowledgeBaseName,
  onClose,
  onUploaded,
}: DocumentUploadDialogProps) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [phase, setPhase] = useState<UploadPhase>("idle");
  const [progress, setProgress] = useState(0);
  const fileError = file ? validateDocumentFile(file) : null;
  const uploading = phase !== "idle";

  const uploadMutation = useMutation({
    mutationFn: async (selected: File) => {
      setPhase("uploading");
      setProgress(0);
      const attachment = await uploadChatAttachment(selected, setProgress);
      setPhase("ingesting");
      return ingestKnowledgeDocument(knowledgeBaseName, attachment.fileId, selected.name);
    },
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-documents"] });
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-document-count"] });
      toast.success(
        result.deduplicated ? "相同内容的文档已存在，已复用已有文档。" : "文档已上传并完成解析。",
      );
      onUploaded();
      onClose();
    },
    onError: (error) => toast.error(resolveKnowledgeBaseApiError(error)),
    onSettled: () => setPhase("idle"),
  });

  const close = () => {
    if (!uploadMutation.isPending) {
      onClose();
    }
  };

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          close();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <div className="flex items-start gap-3 pe-8">
            <span className="flex size-9 shrink-0 items-center justify-center bg-muted">
              <Upload className="size-4" />
            </span>
            <div className="space-y-1">
              <DialogTitle>上传文档</DialogTitle>
              <DialogDescription>
                支持 TXT 与 Markdown，单个文件不超过 25 MB。上传后会立即解析、切片并生成向量。
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4">
          <Input
            type="file"
            accept={KNOWLEDGE_DOCUMENT_ACCEPT}
            disabled={uploadMutation.isPending}
            aria-label="选择文档文件"
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              setProgress(0);
            }}
          />
          {file ? (
            <p className="text-xs text-muted-foreground">
              {file.name} · {formatDocumentSize(file.size)}
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">尚未选择文件。</p>
          )}
          {fileError ? <p className="text-xs text-destructive">{fileError}</p> : null}

          {uploading ? (
            <div className="space-y-2">
              <Progress
                value={phase === "uploading" ? progress : 100}
                className={phase === "ingesting" ? "animate-pulse" : undefined}
              />
              <p className="text-xs text-muted-foreground">
                {phase === "uploading" ? `正在上传 ${progress}%` : "上传完成，正在解析并生成索引…"}
              </p>
            </div>
          ) : null}

          {uploadMutation.isError ? (
            <p className="text-xs text-destructive">
              {resolveKnowledgeBaseApiError(uploadMutation.error)}
            </p>
          ) : null}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={close}
            disabled={uploadMutation.isPending}
          >
            取消
          </Button>
          <Button
            type="button"
            disabled={!file || Boolean(fileError) || uploadMutation.isPending}
            onClick={() => {
              if (file) {
                uploadMutation.mutate(file);
              }
            }}
          >
            {uploadMutation.isPending ? <Loader2 className="animate-spin" /> : <Upload />}
            上传并解析
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function validateDocumentFile(file: File): string | null {
  if (file.size === 0) {
    return "文件内容为空。";
  }
  if (file.size > KNOWLEDGE_DOCUMENT_MAX_BYTES) {
    return "文件不能超过 25 MB。";
  }
  if (!/\.(txt|md|markdown)$/i.test(file.name)) {
    return "仅支持 .txt、.md 与 .markdown 文档。";
  }
  return null;
}
