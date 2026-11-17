import type { ReactNode } from "react";

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
import { cn } from "@/lib/utils";

type ConfirmDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  disabled?: boolean;
  desc: ReactNode;
  cancelBtnText?: string;
  confirmText?: ReactNode;
  destructive?: boolean;
  isLoading?: boolean;
  className?: string;
  children?: ReactNode;
} & ({ form: string; handleConfirm?: undefined } | { form?: undefined; handleConfirm: () => void });

export function ConfirmDialog(props: ConfirmDialogProps) {
  const {
    title,
    desc,
    children,
    className,
    confirmText,
    cancelBtnText,
    destructive,
    isLoading,
    disabled = false,
    form,
    handleConfirm,
    ...actions
  } = props;

  return (
    <AlertDialog {...actions}>
      <AlertDialogContent className={cn(className)}>
        <AlertDialogHeader className="text-start">
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div>{desc}</div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        {children}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isLoading}>{cancelBtnText ?? "取消"}</AlertDialogCancel>
          <Button
            type={form ? "submit" : "button"}
            form={form}
            onClick={handleConfirm}
            variant={destructive ? "destructive" : "default"}
            disabled={disabled || isLoading}
          >
            {confirmText ?? "继续"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
