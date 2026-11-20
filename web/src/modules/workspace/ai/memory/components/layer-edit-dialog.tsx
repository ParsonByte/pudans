import { useState } from "react";

import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

type LayerEditDialogProps = {
  title: string;
  description: string;
  initialContent: string;
  hint?: string;
  pending: boolean;
  allowEmpty?: boolean;
  onSave: (content: string) => void;
  onClose: () => void;
};

export function LayerEditDialog({
  title,
  description,
  initialContent,
  hint,
  pending,
  allowEmpty = false,
  onSave,
  onClose,
}: LayerEditDialogProps) {
  const [content, setContent] = useState(initialContent);
  const invalid = !allowEmpty && content.trim().length === 0;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
        <div className="space-y-2">
          <Label htmlFor="memory-layer-content">内容</Label>
          <Textarea
            id="memory-layer-content"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            disabled={pending}
            className="min-h-[360px] resize-y font-mono text-[13px] leading-relaxed"
            placeholder="输入记忆内容"
          />
          <p className="text-right text-xs text-muted-foreground">{content.length} 字符</p>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button type="button" disabled={pending || invalid} onClick={() => onSave(content)}>
            {pending ? <Loader2 className="animate-spin" /> : null}
            保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
