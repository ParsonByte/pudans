import { useState } from "react";

import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";

import type { ChatMessage } from "../../lib/chat-api";

type EditBranchDialogProps = {
  target: ChatMessage | null;
  onOpenChange: (open: boolean) => void;
  onConfirm: (message: ChatMessage, content: string) => Promise<boolean>;
};

export function EditBranchDialog({ target, onOpenChange, onConfirm }: EditBranchDialogProps) {
  return (
    <Dialog open={Boolean(target)} onOpenChange={onOpenChange}>
      <DialogContent>
        {target ? (
          <EditBranchForm
            key={target.id}
            target={target}
            onConfirm={onConfirm}
            onSuccess={() => onOpenChange(false)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function EditBranchForm({
  target,
  onConfirm,
  onSuccess,
}: {
  target: ChatMessage;
  onConfirm: (message: ChatMessage, content: string) => Promise<boolean>;
  onSuccess: () => void;
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
        onSuccess();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <DialogHeader>
        <DialogTitle>编辑并分支</DialogTitle>
        <DialogDescription>将以该消息之前的历史创建新会话，并发送编辑后的内容。</DialogDescription>
      </DialogHeader>
      <Textarea
        value={content}
        rows={6}
        aria-label="编辑消息内容"
        onChange={(event) => setContent(event.target.value)}
      />
      <DialogFooter>
        <DialogClose asChild>
          <Button type="button" variant="outline">
            取消
          </Button>
        </DialogClose>
        <Button
          type="button"
          disabled={!content.trim() || submitting}
          onClick={() => void confirm()}
        >
          {submitting ? <Loader2 className="animate-spin" /> : null}
          创建分支
        </Button>
      </DialogFooter>
    </>
  );
}
