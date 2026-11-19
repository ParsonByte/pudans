import type { FormEventHandler, ReactNode } from "react";

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
import { cn } from "@/lib/utils";

type ProviderDialogShellProps = {
  title: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  children: ReactNode;
  onSubmit: FormEventHandler<HTMLFormElement>;
  onClose: () => void;
  isPending?: boolean;
  submitLabel?: string;
  cancelLabel?: string;
  className?: string;
};

export function ProviderDialogShell({
  title,
  description,
  icon,
  children,
  onSubmit,
  onClose,
  isPending = false,
  submitLabel = "保存",
  cancelLabel = "取消",
  className,
}: ProviderDialogShellProps) {
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        className={cn("flex max-h-[90vh] flex-col overflow-hidden sm:max-w-2xl", className)}
      >
        <DialogHeader>
          <div className="flex items-start gap-3 pe-8">
            {icon ? (
              <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                {icon}
              </span>
            ) : null}
            <div className="min-w-0 space-y-1">
              <DialogTitle>{title}</DialogTitle>
              {description ? <DialogDescription>{description}</DialogDescription> : null}
            </div>
          </div>
        </DialogHeader>
        <form onSubmit={onSubmit} className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto py-2 pe-1">{children}</div>
          <DialogFooter className="mx-0 mt-4 mb-0 rounded-none border-t bg-transparent px-0 pt-4 sm:justify-end">
            <Button type="button" variant="outline" onClick={onClose} disabled={isPending}>
              {cancelLabel}
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? <Loader2 className="animate-spin" /> : null}
              {submitLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

type ProviderDialogSectionProps = {
  title?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function ProviderDialogSection({ title, children, className }: ProviderDialogSectionProps) {
  return (
    <section className={cn("space-y-3 rounded-2xl border bg-card/30 p-4", className)}>
      {title ? <h3 className="text-sm font-semibold">{title}</h3> : null}
      {children}
    </section>
  );
}

type ProviderFieldRowProps = {
  label: string;
  htmlFor?: string;
  hint?: string;
  error?: string;
  children: ReactNode;
};

export function ProviderFieldRow({ label, hint, error, children }: ProviderFieldRowProps) {
  return (
    <div className="grid gap-2 sm:grid-cols-[88px_1fr] sm:items-start">
      <span className="pt-2 text-sm text-muted-foreground">{label}</span>
      <div className="min-w-0 space-y-1.5">
        {children}
        {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
        {error ? <p className="text-xs text-destructive">{error}</p> : null}
      </div>
    </div>
  );
}
