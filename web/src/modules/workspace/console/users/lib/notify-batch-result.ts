import { toast } from "sonner";

import type { LocalBatchCreateResult } from "./api";

export function notifyBatchResult(result: LocalBatchCreateResult): void {
  if (result.failures.length === 0) {
    toast.success(`已创建 ${result.created} 个用户。`);
    return;
  }

  const description = result.failures
    .slice(0, 5)
    .map(
      (failure) =>
        `第 ${failure.index} 个用户${failure.username ? `（${failure.username}）` : ""}：${failure.message}`,
    )
    .join("\n");

  toast.error(`已创建 ${result.created} 个用户，${result.failed} 个失败。`, {
    description,
    duration: 10000,
  });
}
