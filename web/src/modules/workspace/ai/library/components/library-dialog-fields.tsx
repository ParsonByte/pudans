import { useId } from "react";

import { FormField } from "@/components/form-field";
import { Switch } from "@/components/ui/switch";

import { AgentMultiSelect } from "./agent-multi-select";

type AgentsFieldProps = {
  value: string[];
  onChange: (value: string[]) => void;
  hint?: string;
  disabled?: boolean;
};

export function AgentsField({ value, onChange, hint, disabled = false }: AgentsFieldProps) {
  const id = useId();

  return (
    <FormField
      label="关联 Agent"
      htmlFor={id}
      hint={hint ?? "留空表示不绑定任何 Agent，运行时不会注入。"}
    >
      <AgentMultiSelect id={id} value={value} onChange={onChange} disabled={disabled} />
    </FormField>
  );
}

type EnabledFieldProps = {
  checked: boolean;
  onChange: (checked: boolean) => void;
  hint?: string;
  disabled?: boolean;
};

export function EnabledField({
  checked,
  onChange,
  hint = "停用后不再注入到关联 Agent。",
  disabled = false,
}: EnabledFieldProps) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border p-4">
      <div className="space-y-1">
        <p className="text-sm font-medium">启用</p>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </div>
      <Switch checked={checked} disabled={disabled} onCheckedChange={onChange} aria-label="启用" />
    </div>
  );
}
