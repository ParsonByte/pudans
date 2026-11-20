import { FileText, X } from "lucide-react";

import {
  Attachment,
  AttachmentAction,
  AttachmentActions,
  AttachmentContent,
  AttachmentDescription,
  AttachmentMedia,
  AttachmentTitle,
} from "@/components/ui/attachment";
import { InputGroupAddon } from "@/components/ui/input-group";

export type ChatAttachment = {
  id: string;
  name: string;
  url?: string;
  file?: File;
  progress?: number;
};

type ChatAttachmentsProps = {
  files: ChatAttachment[];
  onRemove: (id: string) => void;
};

export function ChatAttachments({ files, onRemove }: ChatAttachmentsProps) {
  return (
    <InputGroupAddon align="block-start" className="flex-wrap">
      {files.map((file) => (
        <Attachment
          key={file.id}
          size="sm"
          state={file.progress != null && file.progress < 100 ? "uploading" : "done"}
        >
          <AttachmentMedia variant={file.url ? "image" : "icon"}>
            {file.url ? <img src={file.url} alt={file.name} /> : <FileText />}
          </AttachmentMedia>
          <AttachmentContent>
            <AttachmentTitle>{file.name}</AttachmentTitle>
            {file.progress != null && file.progress < 100 ? (
              <AttachmentDescription>上传中 {file.progress}%</AttachmentDescription>
            ) : null}
          </AttachmentContent>
          <AttachmentActions>
            <AttachmentAction aria-label={`移除 ${file.name}`} onClick={() => onRemove(file.id)}>
              <X />
            </AttachmentAction>
          </AttachmentActions>
        </Attachment>
      ))}
    </InputGroupAddon>
  );
}
