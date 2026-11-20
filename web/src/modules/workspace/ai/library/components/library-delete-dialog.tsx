import { ConfirmDialog } from "@/components/confirm-dialog";

type LibraryDeleteDialogProps = {
  resourceLabel: string;
  name: string | null;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
};

export function LibraryDeleteDialog({
  resourceLabel,
  name,
  pending,
  onConfirm,
  onClose,
}: LibraryDeleteDialogProps) {
  return (
    <ConfirmDialog
      open={Boolean(name)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={`删除${resourceLabel}`}
      desc={
        <>
          确定删除「{name ?? ""}」？关联 Agent 将不再注入该{resourceLabel}，此操作无法恢复。
        </>
      }
      destructive
      confirmText="删除"
      isLoading={pending}
      handleConfirm={onConfirm}
    />
  );
}
