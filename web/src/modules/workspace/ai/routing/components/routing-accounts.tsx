import { KeyRound } from "lucide-react";
import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type { AiProviderAccountItem, AiProviderItem } from "../../providers/lib/api";
import {
  ACCOUNT_TYPE_OPTIONS,
  formatBalance,
  formatNumber,
  formatQuota,
  optionLabel,
} from "../../providers/lib/model";
import type { AiRoutingAccountActivity, AiRoutingLive } from "../lib/api";
import { accountHealthLabel, providerDisplayName } from "../lib/model";

type RoutingAccountsProps = {
  providers: AiProviderItem[];
  accounts: AiProviderAccountItem[];
  live: AiRoutingLive | undefined;
  loading: boolean;
  error: boolean;
  providersPath: string | undefined;
  usagePath: string | undefined;
};

export function RoutingAccounts({
  providers,
  accounts,
  live,
  loading,
  error,
  providersPath,
  usagePath,
}: RoutingAccountsProps) {
  const activity = new Map<string, AiRoutingAccountActivity>();
  for (const item of live?.accounts ?? []) {
    activity.set(`${item.provider}/${item.account}`, item);
  }
  const grouped = providers
    .map((provider) => ({
      provider,
      accounts: accounts.filter((account) => account.spec.provider === provider.metadata.name),
    }))
    .filter((entry) => entry.accounts.length > 0);

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">账号与密钥</h2>
          <p className="text-xs text-muted-foreground">统计自今天的请求。</p>
        </div>
        {usagePath ? (
          <Button variant="ghost" size="sm" asChild>
            <Link href={usagePath}>查看用量</Link>
          </Button>
        ) : null}
      </div>

      {loading ? (
        <div className="space-y-3 px-5 py-5">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : error ? (
        <p className="px-5 py-5 text-xs text-muted-foreground">无法加载账号数据。</p>
      ) : grouped.length === 0 ? (
        <ConsoleEmpty>
          <span className="space-y-2">
            <span className="block">还没有供应商账号。</span>
            {providersPath ? (
              <Link
                href={providersPath}
                className="text-sm font-medium text-primary hover:underline"
              >
                去供应商添加账号
              </Link>
            ) : null}
          </span>
        </ConsoleEmpty>
      ) : (
        <div className="divide-y">
          {grouped.map(({ provider, accounts: providerAccounts }) => {
            const totals = providerAccounts.reduce(
              (sum, account) => {
                const item = activity.get(`${provider.metadata.name}/${account.metadata.name}`);
                return {
                  requests: sum.requests + (item?.requests ?? 0),
                  failed: sum.failed + (item?.failed ?? 0),
                };
              },
              { requests: 0, failed: 0 },
            );
            return (
              <div key={provider.metadata.name}>
                <div className="flex flex-wrap items-center gap-2 bg-muted/20 px-5 py-2.5">
                  <ProviderBrandIcon provider={provider} className="size-4" />
                  <span className="text-sm font-medium">
                    {providerDisplayName(provider, provider.metadata.name)}
                  </span>
                  <Badge variant="outline">{providerAccounts.length} 个账号</Badge>
                  {provider.spec.enabled === false ? (
                    <Badge variant="outline">供应商已停用</Badge>
                  ) : null}
                  <span className="ms-auto text-[11px] text-muted-foreground">
                    今日 {formatNumber(totals.requests)} 次请求
                    {totals.failed > 0 ? ` · ${formatNumber(totals.failed)} 失败` : ""}
                  </span>
                </div>
                <div className="divide-y">
                  {providerAccounts.map((account) => (
                    <AccountRow
                      key={account.metadata.name}
                      account={account}
                      activity={activity.get(`${provider.metadata.name}/${account.metadata.name}`)}
                    />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </ConsoleSection>
  );
}

function AccountRow({
  account,
  activity,
}: {
  account: AiProviderAccountItem;
  activity: AiRoutingAccountActivity | undefined;
}) {
  const enabled = account.spec.enabled;
  return (
    <div className="flex flex-wrap items-center gap-3 px-5 py-3">
      <span className={`size-2 shrink-0 ${enabled ? "bg-success" : "bg-muted-foreground/50"}`} />
      <span className="flex size-8 shrink-0 items-center justify-center bg-muted/50">
        <KeyRound className="size-4 text-muted-foreground" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="truncate text-sm font-medium">
            {account.spec.displayName || account.metadata.name}
          </span>
          <Badge variant="outline">
            {optionLabel(ACCOUNT_TYPE_OPTIONS, account.spec.accountType)}
          </Badge>
          {account.spec.freePlan ? <Badge variant="secondary">免费套餐</Badge> : null}
          {!enabled ? <Badge variant="outline">已停用</Badge> : null}
        </div>
        <p className="mt-0.5 truncate text-[11px] text-muted-foreground">
          {formatQuota(account.spec.quota)} · {formatBalance(account.spec.balance)}
        </p>
      </div>
      <div className="text-end text-[11px]">
        <p className="text-muted-foreground">
          {activity
            ? `尝试 ${formatNumber(activity.requests)} · 成功 ${formatNumber(activity.success)}${
                activity.failed > 0 ? ` · 失败 ${formatNumber(activity.failed)}` : ""
              }`
            : "今日暂无请求"}
        </p>
        {account.status?.healthScore != null ? (
          <p className="text-muted-foreground">
            健康度 {formatNumber(account.status.healthScore, 1)}
          </p>
        ) : null}
        {account.status?.health ? (
          <p className="text-muted-foreground">状态 {accountHealthLabel(account.status.health)}</p>
        ) : null}
        {account.status?.lastError ? (
          <p className="max-w-64 truncate text-destructive" title={account.status.lastError}>
            最近错误 {account.status.lastError}
          </p>
        ) : null}
      </div>
    </div>
  );
}
