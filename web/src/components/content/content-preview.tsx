"use client";

import { Eye } from "lucide-react";

import { MarkdownContent } from "@/components/content/markdown";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export function codeFence(content: string, language = ""): string {
  const fence = content.includes("```") ? "````" : "```";
  return `${fence}${language}\n${content}\n${fence}`;
}

export type ContentPreview = {
  title: string;
  description?: string;
  content: string;
};

type ContentPreviewButtonProps = {
  onClick: () => void;
  label?: string;
};

export function ContentPreviewButton({ onClick, label = "预览" }: ContentPreviewButtonProps) {
  return (
    <Button type="button" variant="ghost" size="icon-sm" aria-label={label} onClick={onClick}>
      <Eye />
    </Button>
  );
}

type ContentPreviewDialogProps = {
  preview: ContentPreview | null;
  onClose: () => void;
};

export function ContentPreviewDialog({ preview, onClose }: ContentPreviewDialogProps) {
  return (
    <Dialog
      open={preview !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="max-h-[85vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{preview?.title}</DialogTitle>
          <DialogDescription>{preview?.description || "只读预览"}</DialogDescription>
        </DialogHeader>
        {preview ? <MarkdownContent content={preview.content} /> : null}
      </DialogContent>
    </Dialog>
  );
}
