export type KeyValueEntry = {
  key: string;
  value: string;
};

export function resourceGenerateName(displayName: string, fallback: string): string {
  const slug = displayName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40);
  return `${slug || fallback}-`;
}

export function linesToList(value: string): string[] {
  return value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

export function listToLines(values: readonly string[] | undefined): string {
  return (values ?? []).join("\n");
}

export function recordToEntries(record: Record<string, string> | undefined): KeyValueEntry[] {
  return Object.entries(record ?? {}).map(([key, value]) => ({ key, value }));
}

export function entriesToRecord(entries: readonly KeyValueEntry[]): Record<string, string> {
  const record: Record<string, string> = {};
  for (const entry of entries) {
    const key = entry.key.trim();
    if (key) {
      record[key] = entry.value;
    }
  }
  return record;
}

export function keyValueMessage(entries: readonly KeyValueEntry[]): string | undefined {
  const keys = new Set<string>();
  for (const entry of entries) {
    const key = entry.key.trim();
    if (!key) {
      if (entry.value.trim()) {
        return "键不能为空。";
      }
      continue;
    }
    if (keys.has(key)) {
      return `键「${key}」重复。`;
    }
    keys.add(key);
  }
  return undefined;
}

export function sortBy<T>(items: readonly T[], key: (item: T) => string): T[] {
  return [...items].sort((left, right) => key(left).localeCompare(key(right), "zh-CN"));
}

export function fieldMessage(error: unknown): string | undefined {
  if (typeof error === "object" && error !== null && "message" in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string") {
      return message;
    }
  }
  return undefined;
}
