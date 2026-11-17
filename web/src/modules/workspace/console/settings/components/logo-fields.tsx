import { type UseFormReturn, useWatch } from "react-hook-form";

import { Image as ImageIcon } from "lucide-react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

import type { ParameterField, ParameterFormValues } from "../lib/parameter-fields";

type LogoPreviewProps = {
  url: string;
  label: string;
  fallback?: string;
};

export function LogoPreview({ url, label, fallback }: LogoPreviewProps) {
  const previewUrl = url || fallback;

  return (
    <div className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-lg border bg-muted/30 p-2">
      {previewUrl ? (
        <img
          src={previewUrl}
          alt={`${label}预览`}
          className="max-h-full max-w-full object-contain"
        />
      ) : (
        <ImageIcon className="size-5 text-muted-foreground" />
      )}
    </div>
  );
}

type LogoPairProps = {
  form: UseFormReturn<ParameterFormValues>;
  fields: ParameterField[];
};

export function LogoPair({ form, fields }: LogoPairProps) {
  const lightField = fields.find((field) => field.name === "logoLight");
  const darkField = fields.find((field) => field.name === "logoDark");
  const lightValue = useWatch({ control: form.control, name: "logoLight" });
  const darkValue = useWatch({ control: form.control, name: "logoDark" });

  if (!lightField || !darkField) {
    return null;
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_auto]">
        <div className="space-y-4">
          <LogoUrlInput form={form} field={lightField} prefix="日间模式" />
          <LogoUrlInput form={form} field={darkField} prefix="黑暗模式" />
          <p className="text-xs leading-5 text-muted-foreground">
            LOGO 图像地址，用于在左上角展示；请分别提供黑暗模式和日间模式下不同的 LOGO。
          </p>
        </div>

        <div className="flex flex-wrap gap-3">
          <LogoPreviewCard
            url={resolveLogoUrl(lightValue, lightField.defaultValue ?? "")}
            label="日间模式 Logo 预览"
            className="border bg-white"
          />
          <LogoPreviewCard
            url={resolveLogoUrl(darkValue, darkField.defaultValue ?? "")}
            label="黑暗模式 Logo 预览"
            className="border-neutral-800 bg-neutral-950"
          />
        </div>
      </div>
    </div>
  );
}

function LogoUrlInput({
  form,
  field,
  prefix,
}: {
  form: UseFormReturn<ParameterFormValues>;
  field: ParameterField;
  prefix: string;
}) {
  const fieldId = `parameter-logo-${field.name}`;
  const error = form.formState.errors[field.name];
  const message = typeof error?.message === "string" ? error.message : undefined;

  return (
    <div className="space-y-2">
      <Label htmlFor={fieldId} className="sr-only">
        {field.label}
      </Label>
      <div
        className={cn(
          "flex h-9 items-center overflow-hidden rounded-lg border border-input bg-transparent transition-colors focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50 dark:bg-input/30",
          message && "border-destructive ring-3 ring-destructive/20",
        )}
      >
        <span className="flex h-full shrink-0 items-center border-e border-input px-2.5 text-sm text-muted-foreground">
          {prefix}
        </span>
        <Input
          id={fieldId}
          type="url"
          maxLength={field.maxLength}
          aria-invalid={Boolean(message)}
          autoComplete="off"
          className="h-full rounded-none border-0 bg-transparent px-2.5 shadow-none focus-visible:ring-0 dark:bg-transparent"
          {...form.register(field.name)}
        />
      </div>
      {message ? <p className="text-xs text-destructive">{message}</p> : null}
    </div>
  );
}

function LogoPreviewCard({
  url,
  label,
  className,
}: {
  url: string;
  label: string;
  className: string;
}) {
  return (
    <div
      className={cn("flex h-16 min-w-36 items-center justify-center rounded-xl px-4", className)}
    >
      <img src={url} alt={label} className="max-h-9 max-w-32 object-contain" />
    </div>
  );
}

function resolveLogoUrl(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() ? value : fallback;
}
