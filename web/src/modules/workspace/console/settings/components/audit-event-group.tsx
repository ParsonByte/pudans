import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";

import type { AuditEventGroup, AuditMode } from "../lib/audit-events";

type AuditEventGroupProps = {
  group: AuditEventGroup;
  selected: readonly string[];
  mode: AuditMode;
  onToggleItem: (value: string, checked: boolean) => void;
  onToggleGroup: (values: readonly string[], checked: boolean) => void;
};

export function AuditEventGroupSection({
  group,
  selected,
  mode,
  onToggleItem,
  onToggleGroup,
}: AuditEventGroupProps) {
  const selectedValues = new Set(selected);
  const selectedCount = group.items.filter((item) => selectedValues.has(item.value)).length;
  const masterChecked =
    selectedCount === group.items.length ? true : selectedCount > 0 ? "indeterminate" : false;
  const masterId = `audit-${group.key}-all`;

  return (
    <section className="space-y-4 border-t pt-6 first:border-t-0 first:pt-0">
      <div className="space-y-1">
        <h4 className="text-sm font-semibold">{group.label}</h4>
        <p className="text-xs leading-5 text-muted-foreground">{group.description}</p>
      </div>

      <div className="flex items-start gap-2.5 md:pl-10">
        <Checkbox
          id={masterId}
          checked={masterChecked}
          onCheckedChange={(checked) =>
            onToggleGroup(
              group.items.map((item) => item.value),
              checked === true,
            )
          }
          className="mt-0.5"
        />
        <div className="space-y-0.5">
          <Label htmlFor={masterId} className="font-normal">
            全选此类别事件
          </Label>
          <p className="text-xs leading-5 text-muted-foreground">
            {mode === "WHITELIST"
              ? "勾选后，此类别中的所有事件都会被记录。"
              : "勾选后，此类别中的所有事件都会被排除。"}
          </p>
        </div>
      </div>

      <div className="grid gap-x-8 gap-y-3 sm:grid-cols-2 md:pl-10 xl:grid-cols-4">
        {group.items.map((item) => {
          const itemId = `audit-${group.key}-${item.value}`;

          return (
            <div key={item.value} className="flex items-center gap-2 text-sm">
              <Checkbox
                id={itemId}
                checked={selectedValues.has(item.value)}
                onCheckedChange={(checked) => onToggleItem(item.value, checked === true)}
              />
              <Label htmlFor={itemId} className="cursor-pointer font-normal">
                {item.label}
              </Label>
            </div>
          );
        })}
      </div>
    </section>
  );
}
