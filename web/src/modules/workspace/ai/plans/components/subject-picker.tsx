import { useQuery } from "@tanstack/react-query";

import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  MultiSelect,
  type MultiSelectOption,
} from "@/modules/workspace/ai/library/components/multi-select";
import { fetchAllUsers } from "@/modules/workspace/console/users/lib/api";
import { useUserGroups } from "@/modules/workspace/lib/user-groups";

import type { PlanSubjectKind } from "../lib/api";
import { PLAN_SUBJECT_KIND_OPTIONS } from "../lib/model";

type SubjectPickerProps = {
  kind: PlanSubjectKind;
  onKindChange: (kind: PlanSubjectKind) => void;
  value: string[];
  onChange: (value: string[]) => void;
  multiple?: boolean;
  disabled?: boolean;
  lockKind?: boolean;
};

export function SubjectPicker({
  kind,
  onKindChange,
  value,
  onChange,
  multiple = true,
  disabled = false,
  lockKind = false,
}: SubjectPickerProps) {
  const groupsQuery = useUserGroups({ enabled: kind === "UserGroup" });
  const usersQuery = useQuery({
    queryKey: ["users", "all"],
    queryFn: () => fetchAllUsers(),
    enabled: kind === "User",
    retry: false,
    staleTime: 60_000,
  });

  const options: MultiSelectOption[] =
    kind === "UserGroup"
      ? (groupsQuery.data ?? []).map((group) => ({
          value: group.metadata.name,
          label: group.displayName || group.metadata.name,
        }))
      : (usersQuery.data ?? []).flatMap((user) =>
          user.name
            ? [
                {
                  value: user.name,
                  label: user.displayName || user.name,
                  hint: user.email ?? undefined,
                },
              ]
            : [],
        );

  const loading = kind === "UserGroup" ? groupsQuery.isPending : usersQuery.isPending;
  const failed = kind === "UserGroup" ? groupsQuery.isError : usersQuery.isError;

  return (
    <div className="space-y-2">
      {lockKind ? null : (
        <ToggleGroup
          type="single"
          variant="outline"
          size="sm"
          value={kind}
          onValueChange={(next) => {
            if (next === "User" || next === "UserGroup") {
              onKindChange(next);
              onChange([]);
            }
          }}
          disabled={disabled}
        >
          {PLAN_SUBJECT_KIND_OPTIONS.map((option) => (
            <ToggleGroupItem key={option.value} value={option.value}>
              {option.label}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      )}
      <MultiSelect
        value={value}
        onChange={(next) => onChange(multiple ? next : next.slice(-1))}
        options={options}
        placeholder={kind === "UserGroup" ? "选择用户组" : "选择用户"}
        emptyMessage={kind === "UserGroup" ? "没有匹配的用户组。" : "没有匹配的用户。"}
        searchPlaceholder={kind === "UserGroup" ? "搜索用户组" : "搜索用户"}
        loading={loading}
        disabled={disabled}
        invalid={failed}
      />
      {failed ? (
        <p className="text-xs text-destructive">
          无法加载{kind === "UserGroup" ? "用户组" : "用户"}列表，请稍后重试。
        </p>
      ) : null}
      {kind === "User" ? (
        <p className="text-xs text-muted-foreground">
          直接用户绑定优先于用户组绑定，提交时会自动解析为用户的稳定 uid。
        </p>
      ) : null}
    </div>
  );
}
