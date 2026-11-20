import { useMemo, useState } from "react";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Download,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  Upload,
} from "lucide-react";
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
import { ProviderImportDialog } from "./components/provider-import-dialog";
import {
  type AiProviderItem,
  arrangeProviders,
  fetchAllProviderAccounts,
  fetchAllProviderModels,
  fetchProviderAccountBalance,
  fetchProviders,
  resolveAiProviderApiError,
  updateProvider,
} from "./lib/api";
import { buildProviderBundle, bundleFileName, downloadJson } from "./lib/bundle";
import { sortProvidersByOrder } from "./lib/model";
import {
  PROVIDER_GROUP_LABELS,
  type ProviderGroup,
  type ProviderPreset,
  vendorLabel,
} from "./lib/presets";
import { useProviderPresetsQuery } from "./lib/queries";

export function AiProvidersView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const [search, setSearch] = useState("");
  const [customTarget, setCustomTarget] = useState<ProviderPreset | null | undefined>(undefined);
  const [editingProvider, setEditingProvider] = useState<AiProviderItem | null>(null);
  const [importOpen, setImportOpen] = useState(false);

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
  const presetsQuery = useProviderPresetsQuery();

  const providers = useMemo(
    () => sortProvidersByOrder(providersQuery.data?.items ?? []),
    [providersQuery.data],
  );
  const allAccounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
  const allModels = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const accountCounts = useMemo(
    () => countBy(allAccounts, (account) => account.spec.provider),
    [allAccounts],
  );
  const modelCounts = useMemo(
    () => countBy(allModels, (model) => model.spec.provider),
    [allModels],
  );
  const accountsByProvider = useMemo(
    () => groupBy(allAccounts, (account) => account.spec.provider),
    [allAccounts],
  );
  const modelsByProvider = useMemo(
    () => groupBy(allModels, (model) => model.spec.provider),
    [allModels],
  );
  const providerNames = useMemo(
    () => new Set(providers.map((item) => item.metadata.name)),
    [providers],
  );
  const accountNames = useMemo(
    () => new Set(allAccounts.map((item) => item.metadata.name)),
    [allAccounts],
  );
  const modelNames = useMemo(
    () => new Set(allModels.map((item) => item.metadata.name)),
    [allModels],
  );
  const providerVendors = useMemo(
    () => new Map(providers.map((provider) => [provider.metadata.name, provider.spec.vendor])),
    [providers],
  );
  const balanceAccounts = useMemo(
    () =>
      allAccounts.filter(
        (account) =>
          account.spec.enabled && providerVendors.get(account.spec.provider) === "DEEPSEEK",
      ),
    [allAccounts, providerVendors],
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
    const source = presetsQuery.data?.supported ?? [];
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return source;
    }
    return source.filter(
      (preset) =>
        preset.displayName.toLowerCase().includes(keyword) ||
        preset.regions.some((region) => region.baseUrl.toLowerCase().includes(keyword)),
    );
  }, [search, presetsQuery.data]);

  const presetGroups = useMemo(
    () =>
      (["international", "domestic"] as ProviderGroup[])
        .map((group) => ({
          key: group as string,
          label: PROVIDER_GROUP_LABELS[group],
          items: presets.filter((preset) => preset.group === group),
        }))
        .filter((entry) => entry.items.length > 0),
    [presets],
  );

  const unsupportedPresets = useMemo(() => {
    const source = presetsQuery.data?.unsupported ?? [];
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return source;
    }
    return source.filter(
      (preset) =>
        preset.displayName.toLowerCase().includes(keyword) ||
        preset.description.toLowerCase().includes(keyword) ||
        (preset.unsupportedReason ?? "").toLowerCase().includes(keyword),
    );
  }, [search, presetsQuery.data]);

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

  const arrangeMutation = useMutation({
    mutationFn: (names: string[]) => arrangeProviders(names),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-providers"] });
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const moveProvider = (provider: AiProviderItem, offset: -1 | 1) => {
    const names = providers.map((item) => item.metadata.name);
    const index = names.indexOf(provider.metadata.name);
    const target = index + offset;
    if (index < 0 || target < 0 || target >= names.length) {
      return;
    }
    [names[index], names[target]] = [names[target], names[index]];
    arrangeMutation.mutate(names);
  };

  const loadingAll = providersQuery.isPending || accountsQuery.isPending || modelsQuery.isPending;
  const exportAll = () => {
    const bundle = buildProviderBundle(
      providers.map((provider) => ({
        provider,
        accounts: accountsByProvider.get(provider.metadata.name) ?? [],
        models: modelsByProvider.get(provider.metadata.name) ?? [],
      })),
    );
    downloadJson(bundleFileName("all-providers"), bundle);
    toast.success(`已导出 ${providers.length} 个供应商，文件不包含密钥。`);
  };
  const invalidateImported = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["ai-providers"] }),
      queryClient.invalidateQueries({ queryKey: ["ai-provider-accounts"] }),
      queryClient.invalidateQueries({ queryKey: ["ai-provider-models"] }),
    ]);
  };

  const refreshing =
    providersQuery.isFetching ||
    accountsQuery.isFetching ||
    modelsQuery.isFetching ||
    presetsQuery.isFetching;
  const refresh = () => {
    void providersQuery.refetch();
    void accountsQuery.refetch();
    void modelsQuery.refetch();
    void presetsQuery.refetch();
  };

  return (
    <ConsolePage title="供应商">
      <div className="flex flex-1 flex-col gap-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-base font-semibold">当前供应商</h2>
            <p className="text-xs text-muted-foreground">
              {providers.length} 个供应商，按排序参与路由；可用卡片右侧箭头调整。
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {canCreate ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => setImportOpen(true)}
                disabled={loadingAll}
              >
                <Upload />
                导入
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              onClick={exportAll}
              disabled={loadingAll || providers.length === 0}
            >
              <Download />
              导出
            </Button>
            <Button type="button" variant="outline" onClick={refresh} disabled={refreshing}>
              {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          </div>
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
            {providers.map((provider, index) => (
              <ProviderCard
                key={provider.metadata.name}
                provider={provider}
                accountCount={accountCounts.get(provider.metadata.name)}
                modelCount={modelCounts.get(provider.metadata.name)}
                balance={balances.get(provider.metadata.name)}
                canUpdate={canUpdate}
                canArrange={canUpdate && !arrangeMutation.isPending}
                canMoveUp={index > 0}
                canMoveDown={index < providers.length - 1}
                toggling={
                  toggleMutation.isPending &&
                  toggleMutation.variables?.metadata.name === provider.metadata.name
                }
                onToggle={() => toggleMutation.mutate(provider)}
                onOpen={() => setEditingProvider(provider)}
                onMoveUp={() => moveProvider(provider, -1)}
                onMoveDown={() => moveProvider(provider, 1)}
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
            {presetsQuery.isPending ? (
              <div className="grid gap-3 px-5 py-5 sm:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: 6 }, (_, index) => (
                  <Skeleton key={index} className="h-24 rounded-2xl" />
                ))}
              </div>
            ) : presetsQuery.isError ? (
              <div className="flex flex-col items-start gap-3 px-5 py-8">
                <div className="space-y-1">
                  <p className="text-sm font-medium">无法加载供应商预设</p>
                  <p className="text-xs text-muted-foreground">
                    {resolveAiProviderApiError(presetsQuery.error)}
                  </p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => void presetsQuery.refetch()}
                >
                  重试
                </Button>
              </div>
            ) : presetGroups.length === 0 ? (
              <ConsoleEmpty>没有匹配的供应商预设，使用自定义供应商。</ConsoleEmpty>
            ) : (
              <div className="space-y-6 px-5 py-5">
                {presetGroups.map(({ key, label, items }) => (
                  <div key={key} className="space-y-3">
                    <h3 className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
                      {label}
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
                              <span>{vendorLabel(preset.vendor)}</span>
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
            {!presetsQuery.isPending && !presetsQuery.isError && unsupportedPresets.length > 0 ? (
              <div className="space-y-2 border-t bg-muted/20 px-5 py-4">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-sm font-medium">暂不支持</h3>
                  <Badge variant="outline">平台限制</Badge>
                </div>
                <p className="text-xs text-muted-foreground">
                  以下供应商依赖桌面桥接、订阅入口或平台不支持的端点协议，暂不提供一键预设。
                </p>
                <div className="flex flex-wrap gap-2">
                  {unsupportedPresets.map((preset) => (
                    <div
                      key={preset.id}
                      className="flex flex-wrap items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-xs text-muted-foreground"
                    >
                      <span className="font-medium text-foreground">{preset.displayName}</span>
                      <span>{preset.unsupportedReason || preset.description}</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}
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
      {importOpen ? (
        <ProviderImportDialog
          existingProviderNames={providerNames}
          existingAccountNames={accountNames}
          existingModelNames={modelNames}
          onImported={invalidateImported}
          onClose={() => setImportOpen(false)}
        />
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
  canArrange: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  toggling: boolean;
  onToggle: () => void;
  onOpen: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
};

function ProviderCard({
  provider,
  accountCount,
  modelCount,
  balance,
  canUpdate,
  canArrange,
  canMoveUp,
  canMoveDown,
  toggling,
  onToggle,
  onOpen,
  onMoveUp,
  onMoveDown,
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
        <div className="flex flex-col">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-6"
            disabled={!canArrange || !canMoveUp}
            onClick={onMoveUp}
            aria-label="上移供应商"
            title="上移"
          >
            <ChevronUp className="size-3.5" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-6"
            disabled={!canArrange || !canMoveDown}
            onClick={onMoveDown}
            aria-label="下移供应商"
            title="下移"
          >
            <ChevronDown className="size-3.5" />
          </Button>
        </div>
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

function groupBy<T>(items: readonly T[], key: (item: T) => string): Map<string, T[]> {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const name = key(item);
    const group = groups.get(name);
    if (group) {
      group.push(item);
    } else {
      groups.set(name, [item]);
    }
  }
  return groups;
}
