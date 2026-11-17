import type { ReactNode } from "react";

import { X } from "lucide-react";

import { FloatingActionBar } from "@/components/floating-action-bar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";

type DataTableBulkActionsProps = {
  selectedCount: number;
  entityName: string;
  onClear: () => void;
  children: ReactNode;
};

export function DataTableBulkActions({
  selectedCount,
  entityName,
  onClear,
  children,
}: DataTableBulkActionsProps) {
  return (
    <FloatingActionBar open={selectedCount > 0} className="z-[60]">
      <Button
        type="button"
        variant="outline"
        size="icon-sm"
        onClick={onClear}
        aria-label="清除选择"
      >
        <X />
      </Button>
      <Separator orientation="vertical" className="h-5" />
      <div className="flex items-center gap-1 text-sm">
        <Badge className="min-w-8 rounded-lg">{selectedCount}</Badge>
        <span>个{entityName}已选择</span>
      </div>
      <Separator orientation="vertical" className="h-5" />
      <div className="flex items-center gap-1">{children}</div>
    </FloatingActionBar>
  );
}
