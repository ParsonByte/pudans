const BUILTIN_RESOURCE_LABEL = "pudans.tolim.cn/builtin";

const SYSTEM_PROTECTION_FINALIZER = "system-protection";

type ExtensionRecordMetadata = {
  name: string;
  version?: number;
  labels?: Record<string, string>;
  annotations?: Record<string, string>;
  finalizers?: string[];
  creationTimestamp?: string;
  deletionTimestamp?: string;
  generateName?: string;
};

export type ExtensionRecord = {
  apiVersion: string;
  kind: string;
  metadata: ExtensionRecordMetadata;
  spec?: Record<string, unknown>;
  data?: Record<string, string>;
  stringData?: Record<string, string>;
  type?: string;
};

export function normalizeExtensionRecord(value: unknown): ExtensionRecord {
  const record = asRecord(value);
  const metadata = asRecord(record.metadata);

  return {
    apiVersion: asString(record.apiVersion),
    kind: asString(record.kind),
    metadata: {
      name: asString(metadata.name),
      version: asNumber(metadata.version),
      labels: asStringMap(metadata.labels),
      annotations: asStringMap(metadata.annotations),
      finalizers: Array.isArray(metadata.finalizers)
        ? metadata.finalizers.filter((item): item is string => typeof item === "string")
        : undefined,
      creationTimestamp: asOptionalString(metadata.creationTimestamp),
      deletionTimestamp: asOptionalString(metadata.deletionTimestamp),
      generateName: asOptionalString(metadata.generateName),
    },
    spec: asOptionalRecord(record.spec),
    data: asStringMap(record.data),
    stringData: asStringMap(record.stringData),
    type: asOptionalString(record.type),
  };
}

function isBuiltinExtension(record: ExtensionRecord): boolean {
  return record.metadata.labels?.[BUILTIN_RESOURCE_LABEL] === "true";
}

function hasSystemProtection(record: ExtensionRecord): boolean {
  return Boolean(record.metadata.finalizers?.includes(SYSTEM_PROTECTION_FINALIZER));
}

export function isImmutableExtension(record: ExtensionRecord): boolean {
  return isBuiltinExtension(record) || hasSystemProtection(record);
}

export function mergeExtensionRecord(
  existing: ExtensionRecord | undefined,
  body: Record<string, unknown>,
  envelope: { apiVersion: string; kind: string },
): ExtensionRecord {
  const bodyMetadata = asRecord(body.metadata);
  const bodySpec = asOptionalRecord(body.spec);
  const name = asString(bodyMetadata.name) || existing?.metadata.name || "";

  const record: ExtensionRecord = {
    apiVersion: envelope.apiVersion,
    kind: envelope.kind,
    metadata: {
      ...(existing?.metadata ?? {}),
      name,
    },
    spec: bodySpec ? { ...(existing?.spec ?? {}), ...bodySpec } : existing?.spec,
  };

  if ("data" in body) {
    record.data = asStringMap(body.data);
  } else if (existing?.data) {
    record.data = existing.data;
  }

  if ("stringData" in body) {
    record.stringData = asStringMap(body.stringData);
  } else if (existing?.stringData) {
    record.stringData = existing.stringData;
  }

  if ("type" in body) {
    record.type = asOptionalString(body.type);
  } else if (existing?.type) {
    record.type = existing.type;
  }

  return record;
}

export function toStringMap(value: unknown): Record<string, string> {
  const record = asRecord(value);
  const result: Record<string, string> = {};

  for (const [key, item] of Object.entries(record)) {
    const name = key.trim();
    if (!name) {
      continue;
    }
    if (typeof item === "string" || typeof item === "number" || typeof item === "boolean") {
      result[name] = String(item);
    }
  }

  return result;
}

export function splitList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .filter((item): item is string => typeof item === "string")
      .map((item) => item.trim())
      .filter(Boolean);
  }
  if (typeof value !== "string") {
    return [];
  }
  return value
    .split(/[\s,]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function asOptionalRecord(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

function asStringMap(value: unknown): Record<string, string> | undefined {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return undefined;
  }
  const entries = Object.entries(value as Record<string, unknown>)
    .filter((entry): entry is [string, string] => typeof entry[1] === "string")
    .map(([key, item]) => [key, item] as const);

  return entries.length > 0 ? Object.fromEntries(entries) : undefined;
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function asOptionalString(value: unknown): string | undefined {
  return typeof value === "string" && value ? value : undefined;
}

function asNumber(value: unknown): number | undefined {
  return typeof value === "number" ? value : undefined;
}
