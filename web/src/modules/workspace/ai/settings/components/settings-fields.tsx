import type { ReactNode } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";

import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

export const NO_SELECTION_VALUE = "__none__";

type SettingsSectionProps = {
  title: string;
  description: string;
  children: ReactNode;
};

export function SettingsSection({ title, description, children }: SettingsSectionProps) {
  return (
    <ConsoleSection>
      <ConsoleSectionHeader title={title} description={description} />
      <div className="space-y-5 p-5">{children}</div>
    </ConsoleSection>
  );
}

type NumberFieldProps = {
  id: string;
  label: string;
  registration: UseFormRegisterReturn;
  hint?: string;
  error?: string;
  min?: number;
  step?: number | string;
};

export function NumberField({
  id,
  label,
  registration,
  hint,
  error,
  min = 0,
  step = 1,
}: NumberFieldProps) {
  return (
    <FormField label={label} htmlFor={id} hint={hint} error={error}>
      <Input id={id} type="number" min={min} step={step} {...registration} />
    </FormField>
  );
}

type TextFieldProps = {
  id: string;
  label: string;
  registration: UseFormRegisterReturn;
  hint?: string;
  error?: string;
  placeholder?: string;
};

export function TextField({ id, label, registration, hint, error, placeholder }: TextFieldProps) {
  return (
    <FormField label={label} htmlFor={id} hint={hint} error={error}>
      <Input id={id} placeholder={placeholder} {...registration} />
    </FormField>
  );
}

type SwitchFieldProps = {
  id: string;
  label: string;
  description?: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
};

export function SwitchField({
  id,
  label,
  description,
  checked,
  onCheckedChange,
}: SwitchFieldProps) {
  return (
    <div className="flex items-start justify-between gap-3 border px-3 py-3">
      <div className="space-y-1">
        <Label htmlFor={id}>{label}</Label>
        {description ? <p className="text-[11px] text-muted-foreground">{description}</p> : null}
      </div>
      <Switch id={id} checked={checked} onCheckedChange={onCheckedChange} />
    </div>
  );
}
