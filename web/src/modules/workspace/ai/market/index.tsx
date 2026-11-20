import { type ReactNode, useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { Search } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { ProviderBrandIcon } from "../providers/components/provider-brand-icon";
import {
  type AiModality,
  type AiModelCapability,
  type AiModelItem,
  type AiModelTier,
  type AiModelType,
  type AiProviderItem,
  fetchAllProviderModels,
  fetchProviders,
  resolveAiProviderApiError,
} from "../providers/lib/api";
import { MODEL_TIER_OPTIONS, formatNumber, modelDisplayName } from "../providers/lib/model";
import {
  CAPABILITY_LABELS,
  CONTEXT_FILTER_OPTIONS,
  ENABLED_FILTER_OPTIONS,
  MODALITY_LABELS,
  type ModelContextFilter,
  type ModelEnabledFilter,
  matchesContextFilter,
  matchesEnabledFilter,
  modelCapabilities,
  modelCapabilityLabels,
  modelInputModalities,
  modelOutputModalities,
  modelTierLabel,
  modelTypeLabel,
  toggleSetValue,
} from "./lib/model";

type ModelInfo = {
  model: AiModelItem;
  provider?: AiProviderItem;
  capabilities: AiModelCapability[];
  inputs: AiModality[];
  outputs: AiModality[];
};

export function ModelMarketView() {
  const providersPath = useMenuPath("ai-providers");
  const modelsQuery = useQuery({
    queryKey: ["ai-provider-models", "all"],
    queryFn: fetchAllProviderModels,
    retry: false,
  });
  const providersQuery = useQuery({
    queryKey: ["ai-providers", "all"],
    queryFn: () => fetchProviders(1, 100),
    retry: false,
  });

  const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const providerMap = useMemo(
    () =>
      new Map(
        (providersQuery.data?.items ?? []).map((provider) => [provider.metadata.name, provider]),
      ),
    [providersQuery.data],
  );
  const modelInfos = useMemo<ModelInfo[]>(
    () =>
      models.map((model) => ({
        model,
        provider: providerMap.get(model.spec.provider),
        capabilities: modelCapabilities(model.spec),
        inputs: modelInputModalities(model.spec),
        outputs: modelOutputModalities(model.spec),
      })),
    [models, providerMap],
  );

  const [search, setSearch] = useState("");
  const [types, setTypes] = useState<ReadonlySet<AiModelType>>(new Set());
  const [capabilities, setCapabilities] = useState<ReadonlySet<AiModelCapability>>(new Set());
  const [authors, setAuthors] = useState<ReadonlySet<string>>(new Set());
  const [inputs, setInputs] = useState<ReadonlySet<AiModality>>(new Set());
  const [outputs, setOutputs] = useState<ReadonlySet<AiModality>>(new Set());
  const [tiers, setTiers] = useState<ReadonlySet<AiModelTier>>(new Set());
  const [contextFilter, setContextFilter] = useState<ModelContextFilter>("all");
  const [enabledFilter, setEnabledFilter] = useState<ModelEnabledFilter>("all");
  const [capabilityFilter, setCapabilityFilter] = useState("all");

  const typeOptions = useMemo(() => {
    const present = new Set(modelInfos.map((info) => info.model.spec.modelType));
    return [...present].sort((left, right) => left.localeCompare(right));
  }, [modelInfos]);
  const tierOptions = useMemo(() => {
    const present = new Set<AiModelTier>();
    for (const info of modelInfos) {
      if (info.model.spec.tier) {
        present.add(info.model.spec.tier);
      }
    }
    return MODEL_TIER_OPTIONS.map((option) => option.value).filter((tier) => present.has(tier));
  }, [modelInfos]);
  const capabilityOptions = useMemo(() => {
    const present = new Set<AiModelCapability>();
    for (const info of modelInfos) {
      for (const capability of info.capabilities) {
        present.add(capability);
      }
    }
    return [...present].sort((left, right) =>
      CAPABILITY_LABELS[left].localeCompare(CAPABILITY_LABELS[right]),
    );
  }, [modelInfos]);
  const authorOptions = useMemo(() => {
    const map = new Map<string, { id: string; name: string; provider?: AiProviderItem }>();
    for (const info of modelInfos) {
      const id = info.model.spec.provider;
      if (!map.has(id)) {
        map.set(id, {
          id,
          name: info.provider?.spec.displayName || id,
          provider: info.provider,
        });
      }
    }
    return [...map.values()].sort((left, right) => left.name.localeCompare(right.name));
  }, [modelInfos]);
  const inputOptions = useMemo(() => {
    const present = new Set<AiModality>();
    for (const info of modelInfos) {
      for (const modality of info.inputs) {
        present.add(modality);
      }
    }
    return [...present];
  }, [modelInfos]);
  const outputOptions = useMemo(() => {
    const present = new Set<AiModality>();
    for (const info of modelInfos) {
      for (const modality of info.outputs) {
        present.add(modality);
      }
    }
    return [...present];
  }, [modelInfos]);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    const selectedCapability = capabilityFilter as AiModelCapability;
    return modelInfos.filter((info) => {
      const providerName = info.provider?.spec.displayName || info.model.spec.provider;
      if (
        keyword &&
        !`${info.model.spec.modelCode} ${info.model.spec.displayName ?? ""} ${providerName}`
          .toLowerCase()
          .includes(keyword)
      ) {
        return false;
      }
      if (types.size > 0 && !types.has(info.model.spec.modelType)) {
        return false;
      }
      if (
        capabilities.size > 0 &&
        ![...capabilities].some((item) => info.capabilities.includes(item))
      ) {
        return false;
      }
      if (capabilityFilter !== "all" && !info.capabilities.includes(selectedCapability)) {
        return false;
      }
      if (authors.size > 0 && !authors.has(info.model.spec.provider)) {
        return false;
      }
      if (tiers.size > 0 && (!info.model.spec.tier || !tiers.has(info.model.spec.tier))) {
        return false;
      }
      if (!matchesContextFilter(contextFilter, info.model.spec.contextWindow)) {
        return false;
      }
      if (!matchesEnabledFilter(enabledFilter, info.model.spec.enabled !== false)) {
        return false;
      }
      if (inputs.size > 0 && ![...inputs].every((item) => info.inputs.includes(item))) {
        return false;
      }
      if (outputs.size > 0 && ![...outputs].every((item) => info.outputs.includes(item))) {
        return false;
      }
      return true;
    });
  }, [
    modelInfos,
    search,
    types,
    capabilities,
    capabilityFilter,
    authors,
    tiers,
    contextFilter,
    enabledFilter,
    inputs,
    outputs,
  ]);

  return (
    <ConsolePage title="模型广场">
      <div className="flex flex-1 flex-col gap-6 lg:flex-row">
        <aside className="flex w-full shrink-0 flex-col gap-6 lg:w-56">
          {typeOptions.length > 0 ? (
            <FilterGroup title="模型类别">
              {typeOptions.map((type) => (
                <FilterChip
                  key={type}
                  label={modelTypeLabel(type)}
                  active={types.has(type)}
                  onClick={() => setTypes(toggleSetValue(types, type))}
                />
              ))}
            </FilterGroup>
          ) : null}

          {capabilityOptions.length > 0 ? (
            <FilterGroup title="模型能力">
              {capabilityOptions.map((capability) => (
                <FilterChip
                  key={capability}
                  label={CAPABILITY_LABELS[capability]}
                  active={capabilities.has(capability)}
                  onClick={() => setCapabilities(toggleSetValue(capabilities, capability))}
                />
              ))}
            </FilterGroup>
          ) : null}

          {tierOptions.length > 0 ? (
            <FilterGroup title="模型档位">
              {tierOptions.map((tier) => (
                <FilterChip
                  key={tier}
                  label={modelTierLabel(tier)}
                  active={tiers.has(tier)}
                  onClick={() => setTiers(toggleSetValue(tiers, tier))}
                />
              ))}
            </FilterGroup>
          ) : null}

          <FilterGroup title="上下文窗口">
            {CONTEXT_FILTER_OPTIONS.map((option) => (
              <FilterChip
                key={option.value}
                label={option.label}
                active={contextFilter === option.value}
                onClick={() => setContextFilter(option.value)}
              />
            ))}
          </FilterGroup>

          {authorOptions.length > 0 ? (
            <FilterGroup title="模型作者">
              {authorOptions.map((author) => (
                <FilterChip
                  key={author.id}
                  label={author.name}
                  active={authors.has(author.id)}
                  icon={<ProviderBrandIcon provider={author.provider} className="size-3.5" />}
                  onClick={() => setAuthors(toggleSetValue(authors, author.id))}
                />
              ))}
            </FilterGroup>
          ) : null}

          {inputOptions.length > 0 ? (
            <FilterGroup title="输入">
              {inputOptions.map((modality) => (
                <FilterChip
                  key={modality}
                  label={MODALITY_LABELS[modality]}
                  active={inputs.has(modality)}
                  onClick={() => setInputs(toggleSetValue(inputs, modality))}
                />
              ))}
            </FilterGroup>
          ) : null}

          {outputOptions.length > 0 ? (
            <FilterGroup title="输出">
              {outputOptions.map((modality) => (
                <FilterChip
                  key={modality}
                  label={MODALITY_LABELS[modality]}
                  active={outputs.has(modality)}
                  onClick={() => setOutputs(toggleSetValue(outputs, modality))}
                />
              ))}
            </FilterGroup>
          ) : null}
        </aside>

        <div className="min-w-0 flex-1 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-base font-semibold">
              模型 <span className="text-muted-foreground">{filtered.length} 个</span>
            </h2>
            <div className="flex flex-wrap items-center gap-2">
              <div className="relative">
                <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="搜索模型名称"
                  className="w-64 ps-9"
                  aria-label="搜索模型名称"
                />
              </div>
              <Select value={capabilityFilter} onValueChange={setCapabilityFilter}>
                <SelectTrigger className="w-40">
                  <SelectValue placeholder="全部模型能力" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部模型能力</SelectItem>
                  {capabilityOptions.map((capability) => (
                    <SelectItem key={capability} value={capability}>
                      {CAPABILITY_LABELS[capability]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={enabledFilter}
                onValueChange={(value) => setEnabledFilter(value as ModelEnabledFilter)}
              >
                <SelectTrigger className="w-32">
                  <SelectValue placeholder="全部状态" />
                </SelectTrigger>
                <SelectContent>
                  {ENABLED_FILTER_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {modelsQuery.isPending ? (
            <div className="grid gap-4 sm:grid-cols-2 2xl:grid-cols-3">
              {Array.from({ length: 6 }, (_, index) => (
                <Skeleton key={index} className="h-56 rounded-2xl" />
              ))}
            </div>
          ) : modelsQuery.isError ? (
            <ConsoleSection className="flex min-h-56 flex-col items-center justify-center gap-3 p-8 text-center">
              <p className="text-sm font-medium">无法加载模型</p>
              <p className="text-xs text-muted-foreground">
                {resolveAiProviderApiError(modelsQuery.error)}
              </p>
            </ConsoleSection>
          ) : models.length === 0 ? (
            <ConsoleSection>
              <ConsoleEmpty>
                <div className="space-y-3">
                  <p>还没有模型。先到供应商里添加账号并导入模型。</p>
                  {providersPath ? (
                    <Link
                      to={providersPath}
                      className="inline-flex text-sm font-medium text-primary hover:underline"
                    >
                      去供应商配置
                    </Link>
                  ) : null}
                </div>
              </ConsoleEmpty>
            </ConsoleSection>
          ) : filtered.length === 0 ? (
            <ConsoleSection>
              <ConsoleEmpty>没有匹配的模型，试试调整筛选条件。</ConsoleEmpty>
            </ConsoleSection>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 2xl:grid-cols-3">
              {filtered.map((info) => (
                <ModelCard key={info.model.metadata.name} info={info} />
              ))}
            </div>
          )}
        </div>
      </div>
    </ConsolePage>
  );
}

function ModelCard({ info }: { info: ModelInfo }) {
  const { model, provider } = info;
  const capabilityLabels = modelCapabilityLabels(model.spec);
  const contextWindow = model.spec.contextWindow;
  return (
    <article className="flex min-h-56 flex-col rounded-2xl border bg-card/40 p-5 transition-colors hover:border-border/80 hover:bg-accent/10">
      <div className="flex items-start gap-3">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
          <ProviderBrandIcon provider={provider} className="size-6" />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="truncate text-sm font-semibold">{modelDisplayName(model)}</h3>
          <p className="truncate font-mono text-[11px] text-muted-foreground">
            {model.spec.modelCode}
          </p>
        </div>
        <Badge variant={model.spec.enabled === false ? "outline" : "secondary"}>
          {model.spec.enabled === false ? "已停用" : "已启用"}
        </Badge>
      </div>

      <div className="mt-3 flex flex-wrap gap-1.5">
        <Badge variant="outline">{modelTypeLabel(model.spec.modelType)}</Badge>
        <Badge variant="outline">{modelTierLabel(model.spec.tier)}</Badge>
        {capabilityLabels.slice(0, 5).map((label) => (
          <Badge key={label} variant="outline">
            {label}
          </Badge>
        ))}
      </div>

      <p className="mt-3 line-clamp-3 text-xs leading-5 text-muted-foreground">
        {capabilityLabels.length > 0
          ? `该模型支持${capabilityLabels.join("、")}。`
          : "该模型暂未标注能力。"}
        {` 输入：${info.inputs.map((input) => MODALITY_LABELS[input]).join(" / ")}；输出：${info.outputs
          .map((output) => MODALITY_LABELS[output])
          .join(" / ")}。`}
      </p>

      <div className="mt-auto flex flex-wrap items-center justify-between gap-2 pt-4 text-[11px] text-muted-foreground">
        <span className="flex min-w-0 items-center gap-1.5">
          <ProviderBrandIcon provider={provider} className="size-3.5" />
          <span className="truncate">{provider?.spec.displayName || model.spec.provider}</span>
        </span>
        <span className="whitespace-nowrap">
          {contextWindow ? `上下文 ${formatNumber(contextWindow)}` : "上下文未设置"}
        </span>
      </div>
    </article>
  );
}

function FilterGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="space-y-2.5">
      <h3 className="text-xs font-semibold text-muted-foreground">{title}</h3>
      <div className="flex flex-wrap gap-1.5">{children}</div>
    </div>
  );
}

type FilterChipProps = {
  label: string;
  active: boolean;
  icon?: ReactNode;
  onClick: () => void;
};

function FilterChip({ label, active, icon, onClick }: FilterChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        active
          ? "inline-flex items-center gap-1.5 rounded-full border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary"
          : "inline-flex items-center gap-1.5 rounded-full border border-border/50 bg-muted/40 px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:border-border hover:text-foreground"
      }
    >
      {icon}
      {label}
    </button>
  );
}
