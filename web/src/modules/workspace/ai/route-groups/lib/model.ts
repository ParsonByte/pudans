import {
  AI_API_VERSION,
  AI_ROUTE_GROUP_KIND,
  type AiModelItem,
  type AiModelTier,
  type AiProviderAccountItem,
  type AiProviderAffinity,
  type AiProviderItem,
  type AiRouteGroupItem,
  type AiRouteGroupMember,
  type AiRouteGroupMode,
  type AiRouteGroupTier,
} from "../../providers/lib/api";
import {
  AFFINITY_OPTIONS,
  MODEL_TIER_OPTIONS,
  modelDisplayName,
  optionLabel,
} from "../../providers/lib/model";
import { ROUTE_MODE_LABELS } from "../../routing/lib/model";

export const ROUTE_MODE_OPTIONS = (
  Object.entries(ROUTE_MODE_LABELS) as [AiRouteGroupMode, string][]
).map(([value, label]) => ({ value, label }));

export const ROUTE_GROUP_TIER_OPTIONS: { value: AiRouteGroupTier; label: string }[] = [
  { value: "AUTO", label: "继承" },
  ...MODEL_TIER_OPTIONS,
];

export type RouteTemplate = {
  id: string;
  title: string;
  description: string;
  requirement: string;
  mode: AiRouteGroupMode;
  models: AiModelItem[];
  available: boolean;
  highlighted?: boolean;
};

export function usableModels(
  models: AiModelItem[],
  providers: AiProviderItem[],
  accounts: AiProviderAccountItem[],
): AiModelItem[] {
  const enabledProviders = new Set(
    providers
      .filter((provider) => provider.spec.enabled !== false)
      .map((provider) => provider.metadata.name),
  );
  const providersWithAccounts = new Set(
    accounts.filter((account) => account.spec.enabled).map((account) => account.spec.provider),
  );
  return models.filter(
    (model) =>
      model.spec.enabled !== false &&
      enabledProviders.has(model.spec.provider) &&
      providersWithAccounts.has(model.spec.provider),
  );
}

export function buildRouteTemplates(
  models: AiModelItem[],
  providers: AiProviderItem[],
  accounts: AiProviderAccountItem[],
): RouteTemplate[] {
  const providerNames = [...new Set(models.map((model) => model.spec.provider))];
  const freeAvailable =
    providers.some((provider) => provider.spec.enabled !== false && provider.spec.free) ||
    accounts.some((account) => account.spec.enabled && account.spec.freePlan);
  const strongestByProvider = providerNames
    .map((name) => strongestModels(models.filter((model) => model.spec.provider === name))[0])
    .filter((model): model is AiModelItem => Boolean(model));
  const strongest = strongestModels(models);
  const modelHint = describeModels(models);

  return [
    {
      id: "smart",
      title: "难题给强模型，简单的给便宜模型",
      description: "难的任务交给最强的模型，简单的任务和压缩交给便宜模型。",
      requirement: `需要至少两个可用模型：${modelHint}`,
      mode: "SMART",
      models,
      available: models.length >= 2,
    },
    {
      id: "cheap",
      title: "便宜优先",
      description: "先用便宜模型；需要高推理或便宜模型都失败时，交给最强的模型。",
      requirement: "按成本从低到高尝试可用模型",
      mode: "CHEAP",
      models,
      available: models.length > 0,
    },
    {
      id: "resilient",
      title: "永不卡住",
      description: "每家供应商最强的模型依次互为后备：一家挂了，智能体照常使用。",
      requirement:
        providerNames.length >= 2
          ? "每个供应商的强模型依次后备"
          : `需要两家供应商的模型：目前只有 ${providerNames[0] ?? "未配置"}`,
      mode: "ORDER",
      models: strongestByProvider.length > 0 ? strongestByProvider : models,
      available: providerNames.length >= 2,
    },
    {
      id: "plan",
      title: "计划优先",
      description: "免费或套餐账号优先，按量付费的密钥排在后面。",
      requirement: freeAvailable ? "免费或套餐账号优先" : "需要一个免费或套餐供应商",
      mode: "FREE",
      models,
      available: freeAvailable,
    },
    {
      id: "fast",
      title: "最快优先",
      description: "最近响应最快的供应商排在最前。",
      requirement: "按最近响应速度排序",
      mode: "FAST",
      models,
      available: models.length > 0,
    },
    {
      id: "balanced",
      title: "均匀分担",
      description: "在可用模型和账号之间轮流使用，分摊请求。",
      requirement: "在可用模型和账号之间轮流",
      mode: "ROTATE",
      models: strongest.length > 0 ? strongest : models,
      available: models.length > 0,
      highlighted: true,
    },
  ];
}

export function groupFromTemplate(
  template: RouteTemplate,
  existingNames: ReadonlySet<string>,
): AiRouteGroupItem {
  return {
    apiVersion: AI_API_VERSION,
    kind: AI_ROUTE_GROUP_KIND,
    metadata: { name: uniqueGroupName(`${template.id}-group`, existingNames) },
    spec: {
      displayName: template.title,
      description: template.description,
      routeMode: template.mode,
      defaultTier: "AUTO",
      affinity: "AUTO",
      isDefault: false,
      enabled: true,
      members: template.models.map((model) => ({
        model: model.metadata.name,
        weight: 1,
        enabled: true,
      })),
    },
  };
}

export function uniqueGroupName(base: string, existing: ReadonlySet<string>): string {
  if (!existing.has(base)) {
    return base;
  }
  let index = 2;
  while (existing.has(`${base}-${index}`)) {
    index += 1;
  }
  return `${base}-${index}`;
}

export function memberDrafts(group: AiRouteGroupItem | undefined): AiRouteGroupMember[] {
  return (group?.spec.members ?? [])
    .filter((member) => Boolean(member.model))
    .map((member) => ({
      model: member.model,
      weight: member.weight ?? 1,
      tierOverride: member.tierOverride,
      enabled: member.enabled !== false,
    }));
}

export function routeGroupTierLabel(tier: AiRouteGroupTier | undefined): string {
  return optionLabel(ROUTE_GROUP_TIER_OPTIONS, tier, "继承");
}

export function affinityLabel(affinity: AiProviderAffinity | undefined): string {
  return optionLabel(AFFINITY_OPTIONS, affinity, "自动");
}

function strongestModels(models: AiModelItem[]): AiModelItem[] {
  const ranked = models.filter((model) => Boolean(model.spec.tier));
  if (ranked.length === 0) {
    return models;
  }
  const max = Math.max(...ranked.map((model) => tierRank(model.spec.tier)));
  return models.filter((model) => tierRank(model.spec.tier) === max);
}

function tierRank(tier: AiModelTier | undefined): number {
  switch (tier) {
    case "ULTRA":
      return 4;
    case "HIGH":
      return 3;
    case "MEDIUM":
      return 2;
    case "CHEAP":
      return 1;
    default:
      return 0;
  }
}

function describeModels(models: AiModelItem[]): string {
  if (models.length === 0) {
    return "还没有可用模型";
  }
  const names = models.slice(0, 2).map((model) => modelDisplayName(model));
  return models.length > 2 ? `${names.join("、")} 等 ${models.length} 个` : names.join("、");
}
