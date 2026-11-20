import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

import type { ChatToolConfirm } from "../../lib/chat-api";

type ToolConfirmDialogProps = {
  confirm: ChatToolConfirm | null;
  onApprove: () => void;
  onDeny: () => void;
};

export function ToolConfirmDialog({ confirm, onApprove, onDeny }: ToolConfirmDialogProps) {
  return (
    <AlertDialog
      open={confirm !== null}
      onOpenChange={(open) => {
        if (!open) {
          onDeny();
        }
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader className="text-start">
          <AlertDialogTitle>需要确认工具调用</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div>
              <p>模型请求调用工具「{confirm?.name || "未知工具"}」。</p>
              {confirm?.message ? (
                <p className="mt-2 text-muted-foreground">{confirm.message}</p>
              ) : null}
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>拒绝</AlertDialogCancel>
          <Button type="button" onClick={onApprove}>
            允许
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
