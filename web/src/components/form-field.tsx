import type { ReactNode } from "react";

import { Label } from "@/components/ui/label";

type FormFieldProps = {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  orientation?: "vertical" | "horizontal";
  children: ReactNode;
};

export function FormField({
  label,
  htmlFor,
  error,
  hint,
  orientation = "vertical",
  children,
}: FormFieldProps) {
  const hintNode = hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null;
  const errorNode = error ? <p className="text-xs text-destructive">{error}</p> : null;

  if (orientation === "horizontal") {
    return (
      <div className="grid gap-x-4 gap-y-1.5 sm:grid-cols-[minmax(0,10rem)_minmax(0,1fr)] sm:items-start">
        <Label htmlFor={htmlFor} className="sm:pt-2">
          {label}
        </Label>
        <div className="min-w-0 space-y-1.5">
          {children}
          {hintNode}
          {errorNode}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hintNode}
      {errorNode}
    </div>
  );
}
