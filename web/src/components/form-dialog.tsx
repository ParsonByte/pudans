import { type FormEventHandler, type ReactNode, useId } from "react";
import type { FieldValues, UseFormReturn } from "react-hook-form";

import { Loader2, Save, Undo2 } from "lucide-react";

import { FloatingActionBar } from "@/components/floating-action-bar";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

import { AutoScrollArea } from "./auto-scroll-area";

type FormDialogProps<T extends FieldValues> = {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: ReactNode;
  icon?: ReactNode;
  form: UseFormReturn<T>;
  onSubmit: FormEventHandler<HTMLFormElement>;
  isPending?: boolean;
  maxHeightRatio?: number;
  contentClassName?: string;
  formClassName?: string;
  submitLabel?: string;
  resetLabel?: string;
  children: ReactNode;
};

export function FormDialog<T extends FieldValues>({
  open,
  onClose,
  title,
  description,
  icon,
  form,
  onSubmit,
  isPending = false,
  maxHeightRatio = 0.5,
  contentClassName,
  formClassName,
  submitLabel = "保存更改",
  resetLabel = "撤销更改",
  children,
}: FormDialogProps<T>) {
  const formId = useId();

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
    >
      <DialogContent className={cn("flex max-h-[75vh] flex-col overflow-hidden", contentClassName)}>
        <DialogHeader>
          <div className="flex items-start gap-3">
            {icon ? (
              <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted">
                {icon}
              </span>
            ) : null}
            <div className="space-y-1">
              <DialogTitle>{title}</DialogTitle>
              {description ? <DialogDescription>{description}</DialogDescription> : null}
            </div>
          </div>
        </DialogHeader>

        <AutoScrollArea maxHeightRatio={maxHeightRatio}>
          <form
            id={formId}
            className={cn("space-y-6", formClassName)}
            onSubmit={onSubmit}
            noValidate
          >
            {children}
          </form>
        </AutoScrollArea>

        <FloatingActionBar open={form.formState.isDirty} className="z-[60]">
          <Button type="button" variant="outline" disabled={isPending} onClick={() => form.reset()}>
            <Undo2 />
            {resetLabel}
          </Button>
          <Button type="submit" form={formId} disabled={isPending}>
            {isPending ? <Loader2 className="animate-spin" /> : <Save />}
            {submitLabel}
          </Button>
        </FloatingActionBar>
      </DialogContent>
    </Dialog>
  );
}

type FormDialogSectionProps = {
  title?: ReactNode;
  className?: string;
  children: ReactNode;
};

export function FormDialogSection({ title, className, children }: FormDialogSectionProps) {
  return (
    <section className={cn("space-y-4", className)}>
      {title ? <h3 className="text-sm font-semibold">{title}</h3> : null}
      {children}
    </section>
  );
}
