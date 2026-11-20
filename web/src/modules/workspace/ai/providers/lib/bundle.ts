import type {
  AiModelItem,
  AiProviderAccountInput,
  AiProviderAccountItem,
  AiProviderItem,
} from "./api";

const PROVIDER_BUNDLE_FORMAT = "pudans-ai-provider-bundle";

export type ProviderBundleEntry = {
  provider: AiProviderItem;
  accounts: AiProviderAccountInput[];
  models: AiModelItem[];
};

export type ProviderBundle = {
  format: typeof PROVIDER_BUNDLE_FORMAT;
  version: 1;
  exportedAt: string;
  providers: ProviderBundleEntry[];
};

export function accountHasSecret(account: AiProviderAccountInput): boolean {
  return Boolean(account.spec.apiKey || account.spec.balanceProbe?.token);
}

function cleanMetadata(metadata: AiProviderItem["metadata"]): AiProviderItem["metadata"] {
  return {
    name: metadata.name,
    labels: metadata.labels,
    annotations: metadata.annotations,
  };
}

function stripAccountSecrets(account: AiProviderAccountItem): AiProviderAccountInput {
  const balanceProbe = account.spec.balanceProbe
    ? { ...account.spec.balanceProbe, token: undefined }
    : undefined;
  return {
    ...account,
    metadata: cleanMetadata(account.metadata),
    status: undefined,
    spec: {
      ...account.spec,
      apiKey: undefined,
      balanceProbe,
    },
  };
}

export function buildProviderBundle(
  entries: readonly ProviderBundleEntry[],
  exportedAt = new Date().toISOString(),
): ProviderBundle {
  return {
    format: PROVIDER_BUNDLE_FORMAT,
    version: 1,
    exportedAt,
    providers: entries.map((entry) => ({
      provider: {
        ...entry.provider,
        metadata: cleanMetadata(entry.provider.metadata),
        status: undefined,
      },
      accounts: entry.accounts.map((account) =>
        stripAccountSecrets({ ...account, spec: { ...account.spec } }),
      ),
      models: entry.models.map((model) => ({
        ...model,
        metadata: cleanMetadata(model.metadata),
        status: undefined,
        spec: { ...model.spec },
      })),
    })),
  };
}

export function downloadJson(fileName: string, data: unknown): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: "application/json;charset=utf-8",
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function bundleFileName(prefix: string): string {
  const slug = prefix
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return `${slug || "ai-provider"}-bundle.json`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function resourceName(value: unknown): string | undefined {
  if (!isRecord(value) || !isRecord(value.metadata)) {
    return undefined;
  }
  const name = value.metadata.name;
  return typeof name === "string" && name.length > 0 ? name : undefined;
}

function parseEntry(value: unknown, index: number): ProviderBundleEntry {
  if (!isRecord(value)) {
    throw new Error(`第 ${index + 1} 项不是有效的供应商对象。`);
  }
  const provider = isRecord(value.provider) ? value.provider : value;
  if (provider.kind !== undefined && provider.kind !== "AiProvider") {
    throw new Error(`第 ${index + 1} 项的 kind 必须是 AiProvider。`);
  }
  const providerName = resourceName(provider);
  if (!providerName) {
    throw new Error(`第 ${index + 1} 项缺少 metadata.name。`);
  }
  if (!isRecord(provider.spec) || typeof provider.spec.vendor !== "string") {
    throw new Error(`供应商 ${providerName} 缺少 spec.vendor。`);
  }
  const accounts = Array.isArray(value.accounts) ? value.accounts : [];
  const models = Array.isArray(value.models) ? value.models : [];
  accounts.forEach((account, accountIndex) => {
    if (
      !isRecord(account) ||
      (account.kind !== undefined && account.kind !== "AiProviderAccount")
    ) {
      throw new Error(`供应商 ${providerName} 的第 ${accountIndex + 1} 个账号无效。`);
    }
    if (!resourceName(account)) {
      throw new Error(`供应商 ${providerName} 的第 ${accountIndex + 1} 个账号缺少 metadata.name。`);
    }
  });
  models.forEach((model, modelIndex) => {
    if (!isRecord(model) || (model.kind !== undefined && model.kind !== "AiModel")) {
      throw new Error(`供应商 ${providerName} 的第 ${modelIndex + 1} 个模型无效。`);
    }
    if (!resourceName(model)) {
      throw new Error(`供应商 ${providerName} 的第 ${modelIndex + 1} 个模型缺少 metadata.name。`);
    }
  });
  return {
    provider: provider as AiProviderItem,
    accounts: accounts as AiProviderAccountInput[],
    models: models as AiModelItem[],
  };
}

export function parseProviderBundle(raw: string): ProviderBundle {
  let payload: unknown;
  try {
    payload = JSON.parse(raw);
  } catch (error) {
    throw new Error(
      error instanceof Error ? `无法解析 JSON：${error.message}` : "无法解析 JSON。",
      { cause: error },
    );
  }
  const entries: unknown[] = Array.isArray(payload)
    ? payload
    : isRecord(payload) && Array.isArray(payload.providers)
      ? payload.providers
      : [payload];
  if (entries.length === 0) {
    throw new Error("导入文件中没有供应商。");
  }
  const parsed = entries.map((entry, index) => parseEntry(entry, index));
  const names = new Set<string>();
  for (const entry of parsed) {
    const name = entry.provider.metadata.name;
    if (names.has(name)) {
      throw new Error(`导入文件中的供应商 ${name} 重复。`);
    }
    names.add(name);
  }
  return {
    format: PROVIDER_BUNDLE_FORMAT,
    version: 1,
    exportedAt: new Date().toISOString(),
    providers: parsed,
  };
}
