import { type FormEventHandler, type ReactNode, useId } from "react";
import type { FieldValues, UseFormReturn } from "react-hook-form";

import { Loader2, Save, Undo2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import { DialogFormShell } from "./dialog-form-shell";

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
  fill?: boolean;
  contentClassName?: string;
  formClassName?: string;
  headerActions?: ReactNode;
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
  fill = false,
  contentClassName,
  formClassName,
  headerActions,
  submitLabel = "保存更改",
  resetLabel = "撤销更改",
  children,
}: FormDialogProps<T>) {
  const formId = useId();

  return (
    <DialogFormShell
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
      title={title}
      description={description}
      icon={icon}
      headerActions={headerActions}
      maxHeightRatio={maxHeightRatio}
      fill={fill}
      contentClassName={contentClassName}
      footerMode="dirty"
      footerVisible={form.formState.isDirty}
      footerClassName="z-[60]"
      footer={
        <>
          <Button type="button" variant="outline" disabled={isPending} onClick={() => form.reset()}>
            <Undo2 />
            {resetLabel}
          </Button>
          <Button type="submit" form={formId} disabled={isPending}>
            {isPending ? <Loader2 className="animate-spin" /> : <Save />}
            {submitLabel}
          </Button>
        </>
      }
    >
      <form id={formId} className={cn("space-y-6", formClassName)} onSubmit={onSubmit} noValidate>
        {children}
      </form>
    </DialogFormShell>
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
