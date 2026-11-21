import { useMemo, useState } from "react";

import { Search, UserRound, UsersRound } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";

import type { BindingSubjectOption } from "../lib/binding-model";

const VISIBLE_LIMIT = 50;

type SubjectSelectorProps = {
  options: readonly BindingSubjectOption[];
  selected: readonly string[];
  onChange: (keys: string[]) => void;
  isLoading?: boolean;
  isError?: boolean;
};

export function SubjectSelector({
  options,
  selected,
  onChange,
  isLoading = false,
  isError = false,
}: SubjectSelectorProps) {
  const [search, setSearch] = useState("");

  if (isLoading) {
    return <Skeleton className="h-56 w-full" />;
  }
  if (isError) {
    return (
      <p className="text-xs text-muted-foreground">无法加载用户与用户组，暂不能选择绑定主体。</p>
    );
  }
  if (options.length === 0) {
    return <p className="text-xs text-muted-foreground">暂无可绑定的用户或用户组。</p>;
  }

  return (
    <SubjectSelectorList
      options={options}
      selected={selected}
      onChange={onChange}
      search={search}
      onSearchChange={setSearch}
    />
  );
}

type SubjectSelectorListProps = {
  options: readonly BindingSubjectOption[];
  selected: readonly string[];
  onChange: (keys: string[]) => void;
  search: string;
  onSearchChange: (value: string) => void;
};

function SubjectSelectorList({
  options,
  selected,
  onChange,
  search,
  onSearchChange,
}: SubjectSelectorListProps) {
  const selectedKeys = useMemo(() => new Set(selected), [selected]);
  const normalizedSearch = search.trim().toLowerCase();
  const filteredOptions = useMemo(() => {
    if (!normalizedSearch) {
      return options;
    }
    return options.filter(
      (option) =>
        option.name.toLowerCase().includes(normalizedSearch) ||
        option.label.toLowerCase().includes(normalizedSearch),
    );
  }, [normalizedSearch, options]);

  const selectedOptions = options.filter((option) => selectedKeys.has(option.key));
  const unselectedOptions = filteredOptions.filter((option) => !selectedKeys.has(option.key));
  const groupOptions = unselectedOptions
    .filter((option) => option.kind === "UserGroup")
    .slice(0, VISIBLE_LIMIT);
  const userOptions = unselectedOptions
    .filter((option) => option.kind === "User")
    .slice(0, VISIBLE_LIMIT);
  const hiddenCount = unselectedOptions.length - groupOptions.length - userOptions.length;

  const toggle = (key: string, checked: boolean) => {
    onChange(checked ? [...selected, key] : selected.filter((selectedKey) => selectedKey !== key));
  };

  return (
    <div className="space-y-3">
      <div className="relative">
        <Search className="pointer-events-none absolute start-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="搜索用户名或用户组..."
          className="ps-8"
          autoComplete="off"
          spellCheck={false}
        />
      </div>

      {selectedOptions.length > 0 ? (
        <div className="space-y-1">
          <p className="text-xs font-medium text-muted-foreground">
            已选择 {selectedOptions.length} 个主体
          </p>
          <div className="grid gap-1 border p-1">
            {selectedOptions.map((option) => (
              <SubjectRow
                key={option.key}
                option={option}
                checked
                onCheckedChange={(checked) => toggle(option.key, checked)}
              />
            ))}
          </div>
        </div>
      ) : null}

      <div className="max-h-64 space-y-2 overflow-y-auto border p-1">
        {groupOptions.length > 0 ? (
          <div className="space-y-1">
            <p className="px-2 pt-1 text-xs font-medium text-muted-foreground">用户组</p>
            {groupOptions.map((option) => (
              <SubjectRow
                key={option.key}
                option={option}
                checked={false}
                onCheckedChange={(checked) => toggle(option.key, checked)}
              />
            ))}
          </div>
        ) : null}
        {userOptions.length > 0 ? (
          <div className="space-y-1">
            <p className="px-2 pt-1 text-xs font-medium text-muted-foreground">用户</p>
            {userOptions.map((option) => (
              <SubjectRow
                key={option.key}
                option={option}
                checked={false}
                onCheckedChange={(checked) => toggle(option.key, checked)}
              />
            ))}
          </div>
        ) : null}
        {unselectedOptions.length === 0 && selectedOptions.length === 0 ? (
          <p className="px-2 py-2 text-xs text-muted-foreground">没有匹配的主体。</p>
        ) : null}
        {hiddenCount > 0 ? (
          <p className="px-2 py-2 text-xs text-muted-foreground">
            还有 {hiddenCount} 个匹配项未显示，请输入关键字缩小范围。
          </p>
        ) : null}
      </div>
    </div>
  );
}

type SubjectRowProps = {
  option: BindingSubjectOption;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
};

function SubjectRow({ option, checked, onCheckedChange }: SubjectRowProps) {
  return (
    <label className="flex cursor-pointer items-center gap-2 px-2 py-1.5 text-sm hover:bg-muted">
      <Checkbox checked={checked} onCheckedChange={(value) => onCheckedChange(value === true)} />
      {option.kind === "UserGroup" ? (
        <UsersRound className="size-3.5 shrink-0 text-muted-foreground" />
      ) : (
        <UserRound className="size-3.5 shrink-0 text-muted-foreground" />
      )}
      <span className="min-w-0 flex-1 truncate">{option.label}</span>
      {option.hint ? (
        <span className="truncate text-xs text-muted-foreground">{option.hint}</span>
      ) : null}
      <Badge variant="secondary" className="shrink-0">
        {option.kind === "UserGroup" ? "用户组" : "用户"}
      </Badge>
    </label>
  );
}
