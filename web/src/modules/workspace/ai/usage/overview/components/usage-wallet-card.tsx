import { useState } from "react";

import { History } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { formatPoints } from "@/modules/workspace/ai/routing/lib/model";

import type { UsageWallet } from "../../lib/api";
import { UsageWalletHistoryDialog } from "./usage-wallet-history-dialog";

type UsageWalletCardProps = {
  wallet: UsageWallet;
};

export function UsageWalletCard({ wallet }: UsageWalletCardProps) {
  const [historyOpen, setHistoryOpen] = useState(false);
  const active = wallet.status === 1;
  const consumedPercent =
    wallet.totalGranted > 0
      ? Math.min(Math.max((wallet.totalConsumed / wallet.totalGranted) * 100, 0), 100)
      : 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle>钱包额度</CardTitle>
        <CardDescription>当前账号的积分余额与累计消耗。</CardDescription>
        <CardAction className="flex items-center gap-2">
          <Badge variant={active ? "secondary" : "destructive"}>{active ? "正常" : "已冻结"}</Badge>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-7 px-2"
            onClick={() => setHistoryOpen(true)}
          >
            <History />
            额度历史
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <p className="text-xs text-muted-foreground">可用积分</p>
          <p className="mt-1 text-3xl font-semibold tracking-tight tabular-nums">
            {formatPoints(wallet.available) ?? "0 点"}
          </p>
          <p className="mt-1 text-[11px] text-muted-foreground">
            余额 {formatPoints(wallet.balance) ?? "0 点"} · 冻结{" "}
            {formatPoints(wallet.frozen) ?? "0 点"}
          </p>
        </div>
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="text-muted-foreground">累计消耗</span>
            <span className="tabular-nums">
              {formatPoints(wallet.totalConsumed) ?? "0 点"} /{" "}
              {formatPoints(wallet.totalGranted) ?? "0 点"}
            </span>
          </div>
          <Progress value={consumedPercent} />
        </div>
        <div className="grid grid-cols-2 gap-3 text-xs">
          <div>
            <p className="text-muted-foreground">累计发放</p>
            <p className="mt-1 font-medium tabular-nums">
              {formatPoints(wallet.totalGranted) ?? "0 点"}
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">每日限额</p>
            <p className="mt-1 font-medium tabular-nums">
              {wallet.dailyLimit == null ? "未设置" : (formatPoints(wallet.dailyLimit) ?? "0 点")}
            </p>
          </div>
        </div>
      </CardContent>
      {historyOpen ? <UsageWalletHistoryDialog onClose={() => setHistoryOpen(false)} /> : null}
    </Card>
  );
}
