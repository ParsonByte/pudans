import type { ReactNode } from "react";

import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type DataTableToolbarProps = {
  searchPlaceholder?: string;
  searchValue: string;
  onSearchChange: (value: string) => void;
  filters?: ReactNode;
  isFiltered?: boolean;
  onReset?: () => void;
  viewOptions?: ReactNode;
};

export function DataTableToolbar({
  searchPlaceholder = "筛选...",
  searchValue,
  onSearchChange,
  filters,
  isFiltered = false,
  onReset,
  viewOptions,
}: DataTableToolbarProps) {
  return (
    <div className="flex items-center justify-between gap-2">
      <div className="flex flex-1 flex-col-reverse items-start gap-y-2 sm:flex-row sm:items-center sm:gap-2">
        <Input
          value={searchValue}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder={searchPlaceholder}
          className="h-8 w-full sm:w-64 lg:w-80"
          autoComplete="off"
          spellCheck={false}
        />
        <div className="flex flex-wrap gap-2">{filters}</div>
        {isFiltered ? (
          <Button type="button" variant="ghost" onClick={onReset} className="h-8 px-2 lg:px-3">
            重置
            <X className="ms-2 size-4" />
          </Button>
        ) : null}
      </div>
      {viewOptions}
    </div>
  );
}
