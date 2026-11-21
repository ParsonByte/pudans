import { useState } from "react";

import { Loader2 } from "lucide-react";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
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
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title={title}
      description={description}
      size="2xl"
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button type="button" disabled={pending || invalid} onClick={() => onSave(content)}>
            {pending ? <Loader2 className="animate-spin" /> : null}
            保存
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
        <FormField label="内容" htmlFor="memory-layer-content">
          <Textarea
            id="memory-layer-content"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            disabled={pending}
            className="min-h-[360px] resize-y font-mono text-[13px] leading-relaxed"
            placeholder="输入记忆内容"
          />
          <p className="text-right text-xs text-muted-foreground">{content.length} 字符</p>
        </FormField>
      </div>
    </DialogFormShell>
  );
}
