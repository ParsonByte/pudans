import type { DeepPartial } from "../types";

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (!isRecord(value)) {
    return false;
  }

  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

/** Deep merges plain objects. Arrays and class instances are replaced, never merged. */
export function deepMerge<T extends Record<string, unknown>>(
  target: T,
  ...sources: Array<DeepPartial<T>>
): T {
  const output: Record<string, unknown> = { ...target };

  for (const source of sources) {
    if (!isPlainObject(source)) {
      continue;
    }

    for (const [key, value] of Object.entries(source)) {
      if (value === undefined) {
        continue;
      }

      const current = output[key];
      output[key] =
        isPlainObject(current) && isPlainObject(value) ? deepMerge(current, value) : value;
    }
  }

  return output as T;
}

export function pick<T extends object, K extends keyof T>(
  source: T,
  keys: readonly K[],
): Pick<T, K> {
  const output = {} as Pick<T, K>;
  for (const key of keys) {
    output[key] = source[key];
  }
  return output;
}

export function omit<T extends object, K extends keyof T>(
  source: T,
  keys: readonly K[],
): Omit<T, K> {
  const output = { ...source };
  for (const key of keys) {
    delete output[key];
  }
  return output;
}
