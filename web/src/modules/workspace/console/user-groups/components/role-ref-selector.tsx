import { Lock } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";

import type { UserGroupRoleOption } from "../lib/user-group-model";

type RoleRefSelectorProps = {
  options: readonly UserGroupRoleOption[];
  selected: readonly string[];
  isLoading?: boolean;
  isError?: boolean;
  onChange: (roleNames: string[]) => void;
};

export function RoleRefSelector({
  options,
  selected,
  isLoading = false,
  isError = false,
  onChange,
}: RoleRefSelectorProps) {
  if (isLoading) {
    return <Skeleton className="h-24 w-full" />;
  }
  if (isError) {
    return <p className="text-xs text-muted-foreground">无法加载角色列表，暂不能调整角色绑定。</p>;
  }
  if (options.length === 0) {
    return <p className="text-xs text-muted-foreground">暂无可绑定的角色。</p>;
  }

  const selectedNames = new Set(selected);
  const toggle = (name: string, checked: boolean) => {
    onChange(checked ? [...selected, name] : selected.filter((item) => item !== name));
  };

  return (
    <div className="max-h-56 overflow-y-auto border p-1">
      {options.map((option) => (
        <label
          key={option.name}
          className="flex cursor-pointer items-center gap-2 px-2 py-1.5 text-sm hover:bg-muted"
          title={option.locked ? "内置角色不可移除" : undefined}
        >
          <Checkbox
            checked={selectedNames.has(option.name)}
            disabled={option.locked}
            onCheckedChange={(checked) => toggle(option.name, checked === true)}
          />
          <span className="min-w-0 flex-1 truncate">{option.label}</span>
          {option.builtin ? (
            <Badge variant="secondary" className="shrink-0">
              内置
            </Badge>
          ) : null}
          {option.locked ? <Lock className="size-3.5 shrink-0 text-muted-foreground" /> : null}
        </label>
      ))}
    </div>
  );
}
