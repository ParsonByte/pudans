import { ArrowDown, ArrowUp, ArrowUpDown, EyeOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export type DataTableSortDirection = "asc" | "desc";

type DataTableColumnHeaderProps = {
  title: string;
  direction?: DataTableSortDirection;
  onSort?: (direction: DataTableSortDirection) => void;
  canHide?: boolean;
  onHide?: () => void;
  className?: string;
};

export function DataTableColumnHeader({
  title,
  direction,
  onSort,
  canHide = false,
  onHide,
  className,
}: DataTableColumnHeaderProps) {
  if (!onSort) {
    return <div className={cn(className)}>{title}</div>;
  }

  return (
    <div className={cn("flex items-center space-x-2", className)}>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-8 px-2 data-[state=open]:bg-accent"
          >
            <span>{title}</span>
            {direction === "desc" ? (
              <ArrowDown className="ms-2 size-4" />
            ) : direction === "asc" ? (
              <ArrowUp className="ms-2 size-4" />
            ) : (
              <ArrowUpDown className="ms-2 size-4" />
            )}
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          <DropdownMenuItem onSelect={() => onSort("asc")}>
            <ArrowUp className="size-3.5 text-muted-foreground/70" />
            升序
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => onSort("desc")}>
            <ArrowDown className="size-3.5 text-muted-foreground/70" />
            降序
          </DropdownMenuItem>
          {canHide && onHide ? (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={onHide}>
                <EyeOff className="size-3.5 text-muted-foreground/70" />
                隐藏列
              </DropdownMenuItem>
            </>
          ) : null}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
