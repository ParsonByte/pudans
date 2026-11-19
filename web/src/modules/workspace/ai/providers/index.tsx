import { useMemo, useState } from "react";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronRight, Loader2, Plus, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { CustomProviderDialog } from "./components/custom-provider-dialog";
import { ProviderBrandIcon } from "./components/provider-brand-icon";
import { ProviderEditorDialog } from "./components/provider-editor-dialog";
import {
  type AiProviderItem,
  fetchAllProviderAccounts,
  fetchAllProviderModels,
  fetchProviderAccountBalance,
  fetchProviders,
  resolveAiProviderApiError,
  updateProvider,
} from "./lib/api";
import {
  PROVIDER_GROUP_LABELS,
  PROVIDER_PRESETS,
  type ProviderGroup,
  type ProviderPreset,
  type ProviderProtocolFamily,
} from "./lib/presets";

const FAMILY_LABELS: Record<ProviderProtocolFamily, string> = {
  openai: "OpenAI",
  responses: "Responses",
  anthropic: "Anthropic",
};

export function AiProvidersView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const [search, setSearch] = useState("");
  const [customTarget, setCustomTarget] = useState<ProviderPreset | null | undefined>(undefined);
  const [editingProvider, setEditingProvider] = useState<AiProviderItem | null>(null);

  const providersQuery = useQuery({
    queryKey: ["ai-providers", "all"],
    queryFn: () => fetchProviders(1, 100),
    retry: false,
  });
  const accountsQuery = useQuery({
    queryKey: ["ai-provider-accounts", "all"],
    queryFn: fetchAllProviderAccounts,
    retry: false,
  });
  const modelsQuery = useQuery({
    queryKey: ["ai-provider-models", "all"],
    queryFn: fetchAllProviderModels,
    retry: false,
  });

  const providers = useMemo(() => providersQuery.data?.items ?? [], [providersQuery.data]);
  const accountCounts = useMemo(
    () => countBy(accountsQuery.data ?? [], (account) => account.spec.provider),
    [accountsQuery.data],
  );
  const modelCounts = useMemo(
    () => countBy(modelsQuery.data ?? [], (model) => model.spec.provider),
    [modelsQuery.data],
  );
  const providerProtocols = useMemo(
    () => new Map(providers.map((provider) => [provider.metadata.name, provider.spec.protocol])),
    [providers],
  );
  const balanceAccounts = useMemo(
    () =>
      (accountsQuery.data ?? []).filter(
        (account) =>
          account.spec.enabled &&
          providerProtocols.get(account.spec.provider)?.startsWith("DEEPSEEK"),
      ),
    [accountsQuery.data, providerProtocols],
  );
  const balanceQueries = useQueries({
    queries: balanceAccounts.map((account) => ({
      queryKey: ["ai-account-balance", account.metadata.name],
      queryFn: () => fetchProviderAccountBalance(account.metadata.name),
      retry: false,
      staleTime: 60_000,
    })),
  });
  const balances = new Map<string, ProviderBalance>();
  balanceAccounts.forEach((account, index) => {
    const data = balanceQueries[index]?.data;
    if (!data?.available) {
      return;
    }
    for (const info of data.balanceInfos ?? []) {
      const total = Number(info.totalBalance);
      if (!Number.isFinite(total)) {
        continue;
      }
      const currency = info.currency ?? "";
      const existing = balances.get(account.spec.provider);
      if (existing && existing.currency === currency) {
        existing.total += total;
      } else if (!existing) {
        balances.set(account.spec.provider, { currency, total });
      }
    }
  });
  const presets = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return PROVIDER_PRESETS;
    }
    return PROVIDER_PRESETS.filter(
      (preset) =>
        preset.displayName.toLowerCase().includes(keyword) ||
        preset.regions.some(
          (region) =>
            region.openai?.baseUrl.toLowerCase().includes(keyword) ||
            region.responses?.baseUrl.toLowerCase().includes(keyword) ||
            region.anthropic?.baseUrl.toLowerCase().includes(keyword),
        ),
    );
  }, [search]);

  const presetGroups: { group: ProviderGroup; items: ProviderPreset[] }[] = (
    ["international", "domestic"] as ProviderGroup[]
  )
    .map((group) => ({
      group,
      items: presets.filter((preset) => preset.group === group),
    }))
    .filter((entry) => entry.items.length > 0);

  const toggleMutation = useMutation({
    mutationFn: (provider: AiProviderItem) =>
      updateProvider({
        ...provider,
        spec: { ...provider.spec, enabled: provider.spec.enabled === false },
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-providers"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-provider"] });
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const refreshing =
    providersQuery.isFetching || accountsQuery.isFetching || modelsQuery.isFetching;
  const refresh = () => {
    void providersQuery.refetch();
    void accountsQuery.refetch();
    void modelsQuery.refetch();
  };

  return (
    <ConsolePage title="供应商">
      <div className="flex flex-1 flex-col gap-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-base font-semibold">当前供应商</h2>
            <p className="text-xs text-muted-foreground">
              {providers.length} 个供应商，管理密钥、模型与端点。
            </p>
          </div>
          <Button type="button" variant="outline" onClick={refresh} disabled={refreshing}>
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>

        {providersQuery.isPending ? (
          <div className="grid gap-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-20 rounded-xl" />
            ))}
          </div>
        ) : providersQuery.isError ? (
          <ConsoleSection className="flex min-h-56 flex-col items-center justify-center gap-3 p-8 text-center">
            <p className="text-sm font-medium">无法加载供应商</p>
            <p className="text-xs text-muted-foreground">
              {resolveAiProviderApiError(providersQuery.error)}
            </p>
            <Button type="button" variant="outline" size="sm" onClick={refresh}>
              重试
            </Button>
          </ConsoleSection>
        ) : providers.length === 0 ? (
          <ConsoleSection>
            <ConsoleEmpty>还没有供应商。从下方选择一个供应商预设，或自定义供应商。</ConsoleEmpty>
          </ConsoleSection>
        ) : (
          <div className="grid gap-3">
            {providers.map((provider) => (
              <ProviderCard
                key={provider.metadata.name}
                provider={provider}
                accountCount={accountCounts.get(provider.metadata.name)}
                modelCount={modelCounts.get(provider.metadata.name)}
                balance={balances.get(provider.metadata.name)}
                canUpdate={canUpdate}
                toggling={
                  toggleMutation.isPending &&
                  toggleMutation.variables?.metadata.name === provider.metadata.name
                }
                onToggle={() => toggleMutation.mutate(provider)}
                onOpen={() => setEditingProvider(provider)}
              />
            ))}
          </div>
        )}

        {canCreate ? (
          <ConsoleSection className="overflow-hidden">
            <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-5">
              <div className="space-y-1">
                <h2 className="text-base font-semibold">添加供应商</h2>
                <p className="text-xs text-muted-foreground">
                  选择官方供应商预设，或自定义兼容地址。
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <div className="relative">
                  <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="查找供应商，或输入地址"
                    className="w-64 ps-9"
                    aria-label="查找供应商"
                  />
                </div>
                <Button type="button" variant="outline" onClick={() => setCustomTarget(null)}>
                  <Plus />
                  自定义供应商
                </Button>
              </div>
            </div>
            {presetGroups.length === 0 ? (
              <ConsoleEmpty>没有匹配的供应商预设，使用自定义供应商。</ConsoleEmpty>
            ) : (
              <div className="space-y-6 px-5 py-5">
                {presetGroups.map(({ group, items }) => (
                  <div key={group} className="space-y-3">
                    <h3 className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
                      {PROVIDER_GROUP_LABELS[group]}
                    </h3>
                    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                      {items.map((preset) => (
                        <button
                          key={preset.id}
                          type="button"
                          onClick={() => setCustomTarget(preset)}
                          className="group flex items-center gap-3 rounded-2xl border bg-card/40 p-4 text-start transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:bg-accent/30 hover:shadow-sm focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                        >
                          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
                            <ProviderBrandIcon preset={preset} className="size-6" />
                          </span>
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-semibold">{preset.displayName}</p>
                            <p className="truncate text-xs text-muted-foreground">
                              {preset.description}
                            </p>
                            <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[10px] text-muted-foreground">
                              <span>
                                {preset.families.map((family) => FAMILY_LABELS[family]).join(" / ")}
                              </span>
                              {preset.regions.length > 1 ? (
                                <>
                                  <span>·</span>
                                  <span>
                                    {preset.regions.map((region) => region.label).join(" / ")}
                                  </span>
                                </>
                              ) : null}
                            </div>
                          </div>
                          <Plus className="size-4 shrink-0 text-muted-foreground transition-colors group-hover:text-primary" />
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </ConsoleSection>
        ) : null}
      </div>

      {customTarget !== undefined ? (
        <CustomProviderDialog
          preset={customTarget ?? undefined}
          onCreated={(provider) => {
            setCustomTarget(undefined);
            setEditingProvider(provider);
          }}
          onClose={() => setCustomTarget(undefined)}
        />
      ) : null}
      {editingProvider ? (
        <ProviderEditorDialog provider={editingProvider} onClose={() => setEditingProvider(null)} />
      ) : null}
    </ConsolePage>
  );
}

type ProviderCardProps = {
  provider: AiProviderItem;
  accountCount: number | undefined;
  modelCount: number | undefined;
  balance: ProviderBalance | undefined;
  canUpdate: boolean;
  toggling: boolean;
  onToggle: () => void;
  onOpen: () => void;
};

function ProviderCard({
  provider,
  accountCount,
  modelCount,
  balance,
  canUpdate,
  toggling,
  onToggle,
  onOpen,
}: ProviderCardProps) {
  const enabled = provider.spec.enabled !== false;
  const healthScore = provider.status?.healthScore;
  return (
    <div className="group flex items-center gap-3 rounded-2xl border bg-card/40 p-4 transition-colors hover:bg-accent/20">
      <button
        type="button"
        onClick={onOpen}
        className="flex min-w-0 flex-1 items-center gap-3 text-start"
      >
        <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
          <ProviderBrandIcon provider={provider} className="size-6" />
        </span>
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="truncate text-sm font-semibold">
              {provider.spec.displayName || provider.metadata.name}
            </span>
            <Badge variant="secondary">{provider.spec.free ? "免费" : "按量付费"}</Badge>
            {!enabled ? <Badge variant="outline">已停用</Badge> : null}
          </div>
          <p className="truncate font-mono text-[11px] text-muted-foreground">
            {provider.spec.baseUrl || "未配置接入地址"}
          </p>
          <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
            <span>{modelCount ?? "—"} 个模型</span>
            <span className="text-border">·</span>
            <span>{accountCount ?? "—"} 个密钥</span>
            {balance ? (
              <>
                <span className="text-border">·</span>
                <span>{formatProviderBalance(balance)}</span>
              </>
            ) : null}
            {healthScore == null ? null : (
              <>
                <span className="text-border">·</span>
                <span>健康 {Math.round(healthScore)}%</span>
              </>
            )}
          </div>
        </div>
        <ChevronRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
      </button>
      <div className="flex shrink-0 items-center gap-2">
        {toggling ? <Loader2 className="size-4 animate-spin text-muted-foreground" /> : null}
        <Switch
          checked={enabled}
          disabled={!canUpdate || toggling}
          onCheckedChange={onToggle}
          aria-label={enabled ? "停用供应商" : "启用供应商"}
        />
      </div>
    </div>
  );
}

type ProviderBalance = {
  currency: string;
  total: number;
};

function formatProviderBalance(balance: ProviderBalance): string {
  const amount = new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(balance.total);
  if (balance.currency === "CNY") {
    return `¥${amount}`;
  }
  if (balance.currency === "USD") {
    return `$${amount}`;
  }
  return `${amount} ${balance.currency}`.trim();
}

function countBy<T>(items: readonly T[], key: (item: T) => string): Map<string, number> {
  const counts = new Map<string, number>();
  for (const item of items) {
    const name = key(item);
    counts.set(name, (counts.get(name) ?? 0) + 1);
  }
  return counts;
}
