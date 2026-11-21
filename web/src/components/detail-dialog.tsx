import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

import { DialogFormShell, type DialogSize } from "./dialog-form-shell";

type DetailDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  size?: DialogSize;
  footer?: ReactNode;
  contentClassName?: string;
  bodyClassName?: string;
  children: ReactNode;
};

export function DetailDialog({
  open,
  onOpenChange,
  title,
  description,
  size = "3xl",
  footer,
  contentClassName,
  bodyClassName,
  children,
}: DetailDialogProps) {
  return (
    <DialogFormShell
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      size={size}
      fill
      footer={footer}
      contentClassName={cn("max-h-[85vh]", contentClassName)}
      bodyClassName={bodyClassName}
    >
      {children}
    </DialogFormShell>
  );
}
