import { FileText } from "lucide-react";

import {
  Attachment,
  AttachmentContent,
  AttachmentDescription,
  AttachmentMedia,
  AttachmentTitle,
} from "@/components/ui/attachment";
import { Progress } from "@/components/ui/progress";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

import { type ChatArtifact, chatAttachmentUrl } from "../../lib/chat-api";

type ArtifactBlockProps = {
  artifact: ChatArtifact;
};

export function ArtifactBlock({ artifact }: ArtifactBlockProps) {
  const url = artifact.url || (artifact.fileId ? chatAttachmentUrl(artifact.fileId) : undefined);
  const kind = artifact.kind?.toUpperCase();
  const mimeType = artifact.mimeType?.toLowerCase() ?? "";
  const name = artifact.name || "生成内容";
  const showProgress = artifact.progress != null && artifact.progress < 100;

  if (!url && !showProgress) {
    return null;
  }

  return (
    <div className="w-full space-y-1.5">
      {showProgress ? (
        <div className="space-y-1.5 rounded-lg border bg-muted/30 px-2.5 py-2 text-xs">
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
          <img
            src={url}
            alt={name}
            className="max-h-72 max-w-full rounded-xl border object-contain"
          />
        </a>
      ) : null}
      {url && (kind === "VIDEO" || mimeType.startsWith("video/")) ? (
        <video src={url} controls className="max-h-72 max-w-full rounded-xl border" />
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
        <a href={url} download={name} className="w-fit max-w-md">
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
      ) : null}
    </div>
  );
}
