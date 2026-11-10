export type ClassValue =
  | string
  | number
  | null
  | undefined
  | false
  | ClassValue[]
  | Record<string, boolean | null | undefined>;

function appendClassName(value: ClassValue, result: string[]): void {
  if (!value) {
    return;
  }
  if (typeof value === "string" || typeof value === "number") {
    result.push(String(value));
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      appendClassName(item, result);
    }
    return;
  }
  if (typeof value === "object") {
    for (const [key, enabled] of Object.entries(value)) {
      if (enabled) {
        result.push(key);
      }
    }
  }
}

/**
 * Tiny dependency-free class name composer used across the UI kit.
 *
 * CSS Modules already produce scoped, conflict-free names, so the shared
 * helper only needs conditional joins — no Tailwind merging or runtime
 * dependency.
 */
export function cn(...inputs: ClassValue[]): string {
  const result: string[] = [];
  for (const input of inputs) {
    appendClassName(input, result);
  }
  return result.join(" ");
}
