import { Label } from "@/components/ui/label";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

import type { UsageScope } from "../lib/api";
import { USAGE_SCOPE_OPTIONS } from "../lib/model";

type UsageScopeToggleProps = {
  value: UsageScope;
  onChange: (value: UsageScope) => void;
  disabled?: boolean;
  idPrefix?: string;
};

export function UsageScopeToggle({
  value,
  onChange,
  disabled = false,
  idPrefix = "usage-scope",
}: UsageScopeToggleProps) {
  return (
    <div className="space-y-1.5">
      <Label id={`${idPrefix}-label`} className="text-xs text-muted-foreground">
        数据范围
      </Label>
      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        value={value}
        onValueChange={(next) => {
          if (next === "self" || next === "all") {
            onChange(next);
          }
        }}
        disabled={disabled}
        aria-labelledby={`${idPrefix}-label`}
      >
        {USAGE_SCOPE_OPTIONS.map((option) => (
          <ToggleGroupItem key={option.value} value={option.value}>
            {option.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  );
}
