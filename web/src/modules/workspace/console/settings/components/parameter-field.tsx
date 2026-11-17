import { Controller, type UseFormReturn } from "react-hook-form";

import { PasswordInput } from "@/components/password-input";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

import type { ParameterField, ParameterFormValues } from "../lib/parameter-fields";
import { LogoPreview } from "./logo-fields";

type ParameterFieldControlProps = {
  form: UseFormReturn<ParameterFormValues>;
  groupKey: string;
  field: ParameterField;
  userGroupOptions: readonly { value: string; label: string }[];
  userGroupsPending: boolean;
};

export function ParameterFieldControl({
  form,
  groupKey,
  field,
  userGroupOptions,
  userGroupsPending,
}: ParameterFieldControlProps) {
  const fieldId = `parameter-${groupKey}-${field.name}`;
  const error = form.formState.errors[field.name];
  const message = typeof error?.message === "string" ? error.message : undefined;
  const isNumber = field.kind === "number";
  const isTextarea = field.kind === "textarea";

  if (field.kind === "boolean") {
    return (
      <div className="space-y-2">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <Label htmlFor={fieldId}>{field.label}</Label>
            {field.hint ? (
              <p className="text-xs leading-5 text-muted-foreground">{field.hint}</p>
            ) : null}
          </div>
          <Controller
            control={form.control}
            name={field.name}
            render={({ field: controlledField }) => (
              <Switch
                id={fieldId}
                checked={Boolean(controlledField.value)}
                onCheckedChange={controlledField.onChange}
                aria-invalid={Boolean(message)}
              />
            )}
          />
        </div>
        {message ? <p className="text-xs text-destructive">{message}</p> : null}
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={fieldId}>{field.label}</Label>
        {field.kind === "readonly" || field.readOnly ? (
          <span className="text-[0.6875rem] text-muted-foreground">只读</span>
        ) : null}
      </div>

      {field.kind === "user-group" ? (
        <Controller
          control={form.control}
          name={field.name}
          render={({ field: controlledField }) => {
            const currentValue = String(controlledField.value ?? "");
            const hasCurrentValue = userGroupOptions.some(
              (option) => option.value === currentValue,
            );

            return (
              <Select
                value={currentValue}
                onValueChange={controlledField.onChange}
                disabled={userGroupsPending}
              >
                <SelectTrigger id={fieldId} className={cn("w-full", fieldWidthClass(field.width))}>
                  <SelectValue placeholder="选择默认用户组" />
                </SelectTrigger>
                <SelectContent>
                  {currentValue && !hasCurrentValue ? (
                    <SelectItem value={currentValue}>{currentValue}</SelectItem>
                  ) : null}
                  {userGroupOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            );
          }}
        />
      ) : field.kind === "select" ? (
        <Controller
          control={form.control}
          name={field.name}
          render={({ field: controlledField }) => (
            <Select
              value={String(controlledField.value ?? "")}
              onValueChange={controlledField.onChange}
            >
              <SelectTrigger id={fieldId} className={cn("w-full", fieldWidthClass(field.width))}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {field.options?.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      ) : isTextarea ? (
        <Textarea
          id={fieldId}
          rows={field.rows ?? 4}
          maxLength={field.maxLength}
          autoComplete="off"
          spellCheck={false}
          aria-invalid={Boolean(message)}
          {...form.register(field.name)}
        />
      ) : field.kind === "password" ? (
        <PasswordInput
          id={fieldId}
          maxLength={field.maxLength}
          aria-invalid={Boolean(message)}
          {...form.register(field.name)}
        />
      ) : field.section === "Logo" ? (
        <Controller
          control={form.control}
          name={field.name}
          render={({ field: controlledField }) => (
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
              <Input
                id={fieldId}
                type="url"
                maxLength={field.maxLength}
                autoComplete="off"
                spellCheck={false}
                aria-invalid={Boolean(message)}
                value={String(controlledField.value ?? "")}
                onChange={controlledField.onChange}
                onBlur={controlledField.onBlur}
                ref={controlledField.ref}
                name={controlledField.name}
              />
              <LogoPreview
                url={typeof controlledField.value === "string" ? controlledField.value : ""}
                fallback={field.defaultValue}
                label={field.label}
              />
            </div>
          )}
        />
      ) : (
        <Input
          id={fieldId}
          type={
            isNumber
              ? "number"
              : field.kind === "url"
                ? "url"
                : field.kind === "email"
                  ? "email"
                  : "text"
          }
          min={field.min}
          max={field.max}
          maxLength={isNumber ? undefined : field.maxLength}
          step={isNumber ? 1 : undefined}
          readOnly={field.kind === "readonly" || field.readOnly}
          autoComplete="off"
          spellCheck={false}
          aria-invalid={Boolean(message)}
          className={cn(
            field.kind === "readonly" || field.readOnly ? "bg-muted/40" : undefined,
            fieldWidthClass(field.width),
          )}
          {...form.register(field.name, isNumber ? { valueAsNumber: true } : undefined)}
        />
      )}

      {field.hint ? <p className="text-xs leading-5 text-muted-foreground">{field.hint}</p> : null}
      {message ? <p className="text-xs text-destructive">{message}</p> : null}
    </div>
  );
}

function fieldWidthClass(width?: ParameterField["width"]): string | undefined {
  if (width === "sm") {
    return "sm:max-w-40";
  }
  if (width === "md") {
    return "sm:max-w-xs";
  }
  return undefined;
}
