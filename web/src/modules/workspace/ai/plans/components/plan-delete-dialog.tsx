import type { ReactNode } from "react";

import { ConfirmDialog } from "@/components/confirm-dialog";

type PlanDeleteDialogProps = {
  open: boolean;
  title: string;
  description: ReactNode;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
};

export function PlanDeleteDialog({
  open,
  title,
  description,
  pending,
  onOpenChange,
  onConfirm,
}: PlanDeleteDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      desc={description}
      destructive
      isLoading={pending}
      confirmText={pending ? "删除中…" : "确认删除"}
      handleConfirm={onConfirm}
    />
  );
}
