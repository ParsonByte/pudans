import { useQuery } from "@tanstack/react-query";

import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatPoints } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { UsageErrorState } from "../../components/usage-state";
import { fetchUsageWalletTransactions, resolveUsageApiError } from "../../lib/api";
import type { UsageWalletTransaction } from "../../lib/api";
import { walletTxnTypeLabel } from "../../lib/model";

const TRANSACTION_LIMIT = 50;

const TABLE_COLUMNS = 6;

type UsageWalletHistoryDialogProps = {
  onClose: () => void;
};

export function UsageWalletHistoryDialog({ onClose }: UsageWalletHistoryDialogProps) {
  const transactionsQuery = useQuery({
    queryKey: ["ai-usage-wallet-transactions"],
    queryFn: () => fetchUsageWalletTransactions(TRANSACTION_LIMIT),
    retry: false,
  });

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>额度历史</DialogTitle>
          <DialogDescription>最近 {TRANSACTION_LIMIT} 条钱包积分流水，最新在前。</DialogDescription>
        </DialogHeader>
        {transactionsQuery.isError ? (
          <UsageErrorState
            title="无法加载额度历史"
            message={resolveUsageApiError(transactionsQuery.error)}
            onRetry={() => void transactionsQuery.refetch()}
          />
        ) : transactionsQuery.isPending ? (
          <div className="space-y-2">
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} className="h-10 w-full" />
            ))}
          </div>
        ) : (
          <div className="max-h-[60vh] overflow-y-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-3">时间</TableHead>
                  <TableHead className="px-3">类型</TableHead>
                  <TableHead className="px-3 text-end">变动</TableHead>
                  <TableHead className="px-3 text-end">余额</TableHead>
                  <TableHead className="px-3">请求</TableHead>
                  <TableHead className="px-3">模型</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactionsQuery.data.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={TABLE_COLUMNS}>
                      <ConsoleEmpty>暂无积分流水。</ConsoleEmpty>
                    </TableCell>
                  </TableRow>
                ) : (
                  transactionsQuery.data.map((transaction) => (
                    <TableRow key={transaction.id}>
                      <TableCell className="px-3 py-2 text-xs text-muted-foreground tabular-nums">
                        {formatUserDateTime(transaction.createdAt)}
                      </TableCell>
                      <TableCell className="px-3 py-2">
                        <TxnTypeBadge transaction={transaction} />
                      </TableCell>
                      <TableCell className="px-3 py-2 text-end text-sm tabular-nums">
                        <Amount value={transaction.amount} />
                      </TableCell>
                      <TableCell className="px-3 py-2 text-end text-sm tabular-nums">
                        {formatPoints(transaction.balanceAfter) ?? "0 点"}
                      </TableCell>
                      <TableCell className="px-3 py-2 font-mono text-[10px] text-muted-foreground">
                        {transaction.requestId || "—"}
                      </TableCell>
                      <TableCell className="px-3 py-2 text-xs">
                        {transaction.modelCode || "—"}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

function TxnTypeBadge({ transaction }: { transaction: UsageWalletTransaction }) {
  const positive = transaction.amount > 0;
  return (
    <Badge variant={positive ? "secondary" : "outline"}>
      {walletTxnTypeLabel(transaction.txnType)}
    </Badge>
  );
}

function Amount({ value }: { value: number }) {
  const formatted = formatNumber(Math.abs(value), 2);
  if (value === 0) {
    return <span className="text-muted-foreground">{formatted} 点</span>;
  }
  return (
    <span className={value > 0 ? "text-emerald-600" : "text-destructive"}>
      {value > 0 ? "+" : "-"}
      {formatted} 点
    </span>
  );
}
