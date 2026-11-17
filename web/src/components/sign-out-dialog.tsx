import { ConfirmDialog } from "@/components/confirm-dialog";

type SignOutDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSignOut: () => void;
};

export function SignOutDialog({ open, onOpenChange, onSignOut }: SignOutDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title="退出登录"
      desc="确定要退出登录吗？退出后需要重新登录才能访问账户。"
      confirmText="退出登录"
      destructive
      handleConfirm={() => {
        onSignOut();
        onOpenChange(false);
      }}
      className="sm:max-w-sm"
    />
  );
}
