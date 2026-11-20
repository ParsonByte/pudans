import type { ReactNode } from "react";

import { Pencil, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";

type ResourceRowActionsProps = {
  canUpdate: boolean;
  canDelete: boolean;
  onEdit: () => void;
  onDelete: () => void;
  children?: ReactNode;
};

export function ResourceRowActions({
  canUpdate,
  canDelete,
  onEdit,
  onDelete,
  children,
}: ResourceRowActionsProps) {
  if (!children && !canUpdate && !canDelete) {
    return <span className="text-xs text-muted-foreground">—</span>;
  }

  return (
    <div className="flex items-center justify-end gap-1">
      {children}
      {canUpdate ? (
        <Button type="button" variant="ghost" size="icon-sm" aria-label="编辑" onClick={onEdit}>
          <Pencil />
        </Button>
      ) : null}
      {canDelete ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="删除"
          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
          onClick={onDelete}
        >
          <Trash2 />
        </Button>
      ) : null}
    </div>
  );
}
