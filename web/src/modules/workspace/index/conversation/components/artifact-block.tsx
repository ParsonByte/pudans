"use client";

import { useState } from "react";

import { Eye, FileText } from "lucide-react";

import { kindOf } from "@/components/documents/document-preview";
import { DocumentPreviewDialog } from "@/components/documents/document-preview-dialog";
import {
  Attachment,
  AttachmentContent,
  AttachmentDescription,
  AttachmentMedia,
  AttachmentTitle,
} from "@/components/ui/attachment";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

import { type ChatArtifact, chatAttachmentUrl, fetchChatAttachmentBytes } from "../../lib/chat-api";

type ArtifactBlockProps = {
  artifact: ChatArtifact;
};

export function ArtifactBlock({ artifact }: ArtifactBlockProps) {
  const url = artifact.url || (artifact.fileId ? chatAttachmentUrl(artifact.fileId) : undefined);
  const kind = artifact.kind?.toUpperCase();
  const mimeType = artifact.mimeType?.toLowerCase() ?? "";
  const name = artifact.name || "生成内容";
  const showProgress = artifact.progress != null && artifact.progress < 100;
  const documentKind = kindOf(artifact.name, artifact.mimeType);
  const previewable = ["docx", "xlsx", "zip", "markdown", "text", "pdf"].includes(documentKind);
  const [previewOpen, setPreviewOpen] = useState(false);

  const loadPreviewBytes = async (): Promise<ArrayBuffer> => {
    if (artifact.fileId) {
      return fetchChatAttachmentBytes(artifact.fileId);
    }
    if (artifact.url) {
      const response = await fetch(artifact.url, { credentials: "include" });
      if (!response.ok) {
        throw new Error(`无法加载文件（${response.status}）。`);
      }
      return response.arrayBuffer();
    }
    throw new Error("文件不可预览。");
  };

  if (!url && !showProgress) {
    return null;
  }

  return (
    <div className="w-full space-y-1.5">
      {showProgress ? (
        <div className="space-y-1.5 border bg-muted/30 px-2.5 py-2 text-xs">
          <div className="flex items-center gap-2">
            <FileText className="size-3.5 text-muted-foreground" />
            <span>生成中…</span>
            <span className="ms-auto text-muted-foreground">{artifact.progress}%</span>
          </div>
          <Progress value={artifact.progress ?? 0} />
        </div>
      ) : null}
      {url && (kind === "IMAGE" || mimeType.startsWith("image/")) ? (
        <a href={url} target="_blank" rel="noreferrer" className="inline-block">
          <img src={url} alt={name} className="max-h-72 max-w-full border object-contain" />
        </a>
      ) : null}
      {url && (kind === "VIDEO" || mimeType.startsWith("video/")) ? (
        <video src={url} controls className="max-h-72 max-w-full border" />
      ) : null}
      {url && (kind === "AUDIO" || mimeType.startsWith("audio/")) ? (
        <audio src={url} controls className="w-full max-w-md" />
      ) : null}
      {url &&
      kind !== "IMAGE" &&
      kind !== "VIDEO" &&
      kind !== "AUDIO" &&
      !mimeType.startsWith("image/") &&
      !mimeType.startsWith("video/") &&
      !mimeType.startsWith("audio/") ? (
        <div className="flex w-fit max-w-md items-center gap-2">
          <a href={url} download={name} className="min-w-0">
            <Attachment className="max-w-full transition-colors hover:bg-muted/50">
              <AttachmentMedia>
                <FileText />
              </AttachmentMedia>
              <AttachmentContent>
                <AttachmentTitle>{name}</AttachmentTitle>
                {artifact.size != null ? (
                  <AttachmentDescription>{formatFileSize(artifact.size)}</AttachmentDescription>
                ) : null}
              </AttachmentContent>
            </Attachment>
          </a>
          {previewable ? (
            <Button type="button" variant="outline" size="sm" onClick={() => setPreviewOpen(true)}>
              <Eye />
              预览
            </Button>
          ) : null}
        </div>
      ) : null}
      <DocumentPreviewDialog
        open={previewOpen}
        onOpenChange={setPreviewOpen}
        document={
          previewable
            ? {
                sourceKey: artifact.fileId ?? artifact.url ?? name,
                name,
                mimeType: artifact.mimeType,
                size: artifact.size,
                loadBytes: loadPreviewBytes,
              }
            : null
        }
      />
    </div>
  );
}
