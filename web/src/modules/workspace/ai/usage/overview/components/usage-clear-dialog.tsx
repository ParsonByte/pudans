import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";

import { clearUsage, resolveUsageApiError } from "../../lib/api";
import type { UsageDateRangeValue } from "../../lib/model";

const CLEAR_CUTOFF_SUFFIX = "T23:59:59+08:00";

type UsageClearDialogProps = {
  open: boolean;
  range: UsageDateRangeValue;
  onOpenChange: (open: boolean) => void;
  onCleared: () => void;
};

export function UsageClearDialog({ open, range, onOpenChange, onCleared }: UsageClearDialogProps) {
  const mutation = useMutation({
    mutationFn: () =>
      clearUsage({ before: new Date(`${range.to}${CLEAR_CUTOFF_SUFFIX}`).toISOString() }),
    onSuccess: (result) => {
      toast.success(
        `已清空用量：请求 ${result.requestsDeleted} 条，模型请求 ${result.modelRequestsDeleted} 条。`,
      );
      onCleared();
      onOpenChange(false);
    },
    onError: (error) => toast.error(resolveUsageApiError(error)),
  });

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title="清空用量"
      desc={`将删除截至 ${range.to} 的 AI 聊天请求与模型请求用量记录，操作不可恢复；套餐窗口计数与用量账本不受影响。仅管理员可执行。`}
      destructive
      isLoading={mutation.isPending}
      confirmText={mutation.isPending ? "清空中…" : "确认清空"}
      handleConfirm={() => mutation.mutate()}
    />
  );
}
