import { type FormEventHandler, type ReactNode, useId } from "react";
import type { FieldValues, UseFormReturn } from "react-hook-form";

import { Loader2, Save, Undo2 } from "lucide-react";

import { FloatingActionBar } from "@/components/floating-action-bar";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type SettingsFormShellProps<T extends FieldValues> = {
  form: UseFormReturn<T>;
  onSubmit: FormEventHandler<HTMLFormElement>;
  isPending: boolean;
  className?: string;
  children: ReactNode;
};

export function SettingsFormShell<T extends FieldValues>({
  form,
  onSubmit,
  isPending,
  className,
  children,
}: SettingsFormShellProps<T>) {
  const formId = useId();

  return (
    <form
      id={formId}
      onSubmit={onSubmit}
      className={cn("max-w-3xl space-y-8", className)}
      aria-busy={isPending}
      noValidate
    >
      {children}
      <FloatingActionBar open={form.formState.isDirty}>
        <Button type="button" variant="outline" disabled={isPending} onClick={() => form.reset()}>
          <Undo2 />
          撤销更改
        </Button>
        <Button type="submit" form={formId} disabled={isPending}>
          {isPending ? <Loader2 className="animate-spin" /> : <Save />}
          保存更改
        </Button>
      </FloatingActionBar>
    </form>
  );
}
