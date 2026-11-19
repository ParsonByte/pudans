import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronRight, Loader2, Plug, Plus, RefreshCw, Search } from "lucide-react";
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
import { ProviderEditorDialog } from "./components/provider-editor-dialog";
import {
  type AiProviderItem,
  fetchAllProviderAccounts,
  fetchAllProviderModels,
  fetchProviders,
  resolveAiProviderApiError,
  updateProvider,
} from "./lib/api";
import { PROVIDER_PRESETS, type ProviderPreset } from "./lib/presets";

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

  const providers = providersQuery.data?.items ?? [];
  const accountCounts = useMemo(
    () => countBy(accountsQuery.data ?? [], (account) => account.spec.provider),
    [accountsQuery.data],
  );
  const modelCounts = useMemo(
    () => countBy(modelsQuery.data ?? [], (model) => model.spec.provider),
    [modelsQuery.data],
  );
  const presets = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return PROVIDER_PRESETS;
    }
    return PROVIDER_PRESETS.filter(
      (preset) =>
        preset.displayName.toLowerCase().includes(keyword) ||
        preset.baseUrl.toLowerCase().includes(keyword),
    );
  }, [search]);

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
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button type="button" variant="outline" onClick={refresh} disabled={refreshing}>
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>

        {providersQuery.isPending ? (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
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
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {providers.map((provider) => (
              <ProviderCard
                key={provider.metadata.name}
                provider={provider}
                accountCount={accountCounts.get(provider.metadata.name)}
                modelCount={modelCounts.get(provider.metadata.name)}
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
          <ConsoleSection>
            <div className="flex flex-wrap items-start justify-between gap-3 px-5 py-4">
              <div className="space-y-1">
                <h2 className="text-sm font-semibold">添加供应商</h2>
                <p className="text-xs text-muted-foreground">
                  从预设开始，或自定义一个兼容的供应商地址和密钥。
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
            {presets.length === 0 ? (
              <ConsoleEmpty>没有匹配的供应商预设，使用自定义供应商。</ConsoleEmpty>
            ) : (
              <div className="grid gap-3 px-5 pb-5 sm:grid-cols-2 xl:grid-cols-3">
                {presets.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => setCustomTarget(preset)}
                    className="flex items-center gap-3 rounded-xl border p-4 text-start transition-colors hover:border-primary/50 hover:bg-accent/30 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                  >
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                      <Plug className="size-4" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{preset.displayName}</p>
                      <p className="truncate text-xs text-muted-foreground">{preset.baseUrl}</p>
                    </div>
                    <Plus className="size-4 shrink-0 text-muted-foreground" />
                  </button>
                ))}
              </div>
            )}
          </ConsoleSection>
        ) : null}
      </div>

      {customTarget !== undefined ? (
        <CustomProviderDialog
          preset={customTarget ?? undefined}
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
  canUpdate: boolean;
  toggling: boolean;
  onToggle: () => void;
  onOpen: () => void;
};

function ProviderCard({
  provider,
  accountCount,
  modelCount,
  canUpdate,
  toggling,
  onToggle,
  onOpen,
}: ProviderCardProps) {
  const enabled = provider.spec.enabled !== false;
  return (
    <div className="flex items-center gap-3 rounded-xl border p-4">
      <button
        type="button"
        onClick={onOpen}
        className="flex min-w-0 flex-1 items-center gap-3 text-start"
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted">
          <Plug className="size-5" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="truncate text-sm font-medium">
              {provider.spec.displayName || provider.metadata.name}
            </span>
            <Badge variant={enabled ? "secondary" : "outline"}>
              {enabled ? "已启用" : "已停用"}
            </Badge>
            {provider.spec.free ? <Badge variant="outline">免费</Badge> : null}
          </div>
          <p className="truncate text-xs text-muted-foreground">
            {provider.spec.baseUrl || "未配置接入地址"}
            {` · ${modelCount ?? "—"} 个模型 · ${accountCount ?? "—"} 个密钥`}
          </p>
        </div>
        <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
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

function countBy<T>(items: readonly T[], key: (item: T) => string): Map<string, number> {
  const counts = new Map<string, number>();
  for (const item of items) {
    const name = key(item);
    counts.set(name, (counts.get(name) ?? 0) + 1);
  }
  return counts;
}
