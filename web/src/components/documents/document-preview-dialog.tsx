"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

import { DocumentPreview } from "./document-preview";

type PreviewDocument = {
  sourceKey: string;
  name?: string;
  mimeType?: string;
  size?: number;
  loadBytes: () => Promise<ArrayBuffer>;
};

type DocumentPreviewDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  document: PreviewDocument | null;
};

export function DocumentPreviewDialog({
  open,
  onOpenChange,
  document,
}: DocumentPreviewDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-5xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{document?.name || "文档预览"}</DialogTitle>
          <DialogDescription>
            {document?.mimeType || "根据文件类型选择预览方式。"}
          </DialogDescription>
        </DialogHeader>
        {document ? (
          <DocumentPreview
            key={document.sourceKey}
            name={document.name}
            mimeType={document.mimeType}
            size={document.size}
            loadBytes={document.loadBytes}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
