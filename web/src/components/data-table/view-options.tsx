import { SlidersHorizontal } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

type DataTableViewOption = {
  key: string;
  label: string;
};

type DataTableViewOptionsProps = {
  options: readonly DataTableViewOption[];
  visible: Record<string, boolean>;
  onToggle: (key: string, visible: boolean) => void;
};

export function DataTableViewOptions({ options, visible, onToggle }: DataTableViewOptionsProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="outline" size="sm" className="ms-auto hidden h-8 lg:flex">
          <SlidersHorizontal className="size-4" />
          视图
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-40">
        <DropdownMenuLabel>显示列</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {options.map((option) => (
          <DropdownMenuCheckboxItem
            key={option.key}
            checked={visible[option.key] !== false}
            onCheckedChange={(checked) => onToggle(option.key, Boolean(checked))}
          >
            {option.label}
          </DropdownMenuCheckboxItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
