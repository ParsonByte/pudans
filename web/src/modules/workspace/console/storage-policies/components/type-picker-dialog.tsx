import { DetailDialog } from "@/components/detail-dialog";

import type { StoragePolicyTypeDescriptor } from "../lib/api";
import { StorageTypeIcon } from "./type-icon";

type TypePickerDialogProps = {
  types: StoragePolicyTypeDescriptor[];
  onSelect: (type: StoragePolicyTypeDescriptor["type"]) => void;
  onClose: () => void;
};

export function TypePickerDialog({ types, onSelect, onClose }: TypePickerDialogProps) {
  return (
    <DetailDialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title="选择存储方式"
      description="选择文件实际保存的位置。创建后仍可修改桶、凭据和上传配置。"
      size="2xl"
    >
      <div className="grid gap-3 sm:grid-cols-2">
        {types.map((type) => (
          <button
            key={type.type}
            type="button"
            onClick={() => onSelect(type.type)}
            className="group flex items-center gap-4 border bg-card p-4 text-start transition-colors hover:border-primary/50 hover:bg-accent/40 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
          >
            <span className="flex size-11 shrink-0 items-center justify-center bg-muted text-muted-foreground transition-colors group-hover:text-foreground">
              <StorageTypeIcon type={type.type} className="size-5" />
            </span>
            <span className="min-w-0 space-y-1">
              <span className="block text-sm font-medium">{type.displayName}</span>
              <span className="block text-xs leading-5 text-muted-foreground">
                {type.description}
              </span>
            </span>
          </button>
        ))}
      </div>
    </DetailDialog>
  );
}
