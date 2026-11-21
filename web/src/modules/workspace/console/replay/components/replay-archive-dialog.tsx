import { ConfirmDialog } from "@/components/confirm-dialog";
import { DateTimePicker } from "@/components/date-time-picker";
import { Label } from "@/components/ui/label";

type ReplayArchiveDialogProps = {
  open: boolean;
  cutoff: string;
  pending: boolean;
  onCutoffChange: (value: string) => void;
  onConfirm: () => void;
  onClose: () => void;
};

export function ReplayArchiveDialog({
  open,
  cutoff,
  pending,
  onCutoffChange,
  onConfirm,
  onClose,
}: ReplayArchiveDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
      title="发起归档"
      desc="将截止时间之前的审计记录移入归档表，归档后可继续查询和回放。"
      confirmText="确认归档"
      disabled={!cutoff}
      isLoading={pending}
      handleConfirm={onConfirm}
    >
      <div className="space-y-2">
        <Label htmlFor="archive-cutoff">归档截止时间</Label>
        <DateTimePicker id="archive-cutoff" value={cutoff} onChange={onCutoffChange} />
        <p className="text-xs text-muted-foreground">早于该时间的活跃审计记录会被移入归档。</p>
      </div>
    </ConfirmDialog>
  );
}
