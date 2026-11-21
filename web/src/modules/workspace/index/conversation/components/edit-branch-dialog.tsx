import { useState } from "react";

import { Loader2 } from "lucide-react";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

import type { ChatMessage } from "../../lib/chat-api";

type EditBranchDialogProps = {
  target: ChatMessage | null;
  onOpenChange: (open: boolean) => void;
  onConfirm: (message: ChatMessage, content: string) => Promise<boolean>;
};

export function EditBranchDialog({ target, onOpenChange, onConfirm }: EditBranchDialogProps) {
  if (!target) {
    return null;
  }

  return (
    <EditBranchForm
      key={target.id}
      target={target}
      onOpenChange={onOpenChange}
      onConfirm={onConfirm}
    />
  );
}

function EditBranchForm({
  target,
  onOpenChange,
  onConfirm,
}: {
  target: ChatMessage;
  onOpenChange: (open: boolean) => void;
  onConfirm: (message: ChatMessage, content: string) => Promise<boolean>;
}) {
  const [content, setContent] = useState(target.content ?? "");
  const [submitting, setSubmitting] = useState(false);

  const confirm = async () => {
    if (!content.trim()) {
      return;
    }
    setSubmitting(true);
    try {
      if (await onConfirm(target, content.trim())) {
        onOpenChange(false);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DialogFormShell
      open
      onOpenChange={onOpenChange}
      title="编辑并分支"
      description="将以该消息之前的历史创建新会话，并发送编辑后的内容。"
      size="md"
      footer={
        <>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={submitting}
          >
            取消
          </Button>
          <Button
            type="button"
            disabled={!content.trim() || submitting}
            onClick={() => void confirm()}
          >
            {submitting ? <Loader2 className="animate-spin" /> : null}
            创建分支
          </Button>
        </>
      }
    >
      <Textarea
        value={content}
        rows={6}
        aria-label="编辑消息内容"
        onChange={(event) => setContent(event.target.value)}
      />
    </DialogFormShell>
  );
}
