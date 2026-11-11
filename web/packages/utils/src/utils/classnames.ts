export type ClassValue =
  | string
  | number
  | null
  | undefined
  | false
  | ClassValue[]
  | Record<string, boolean | null | undefined>;

/** Tiny clsx-compatible class combiner with stable de-duplication. */
export function cn(...inputs: ClassValue[]): string {
  const classes = new Set<string>();

  const add = (value: ClassValue): void => {
    if (!value) {
      return;
    }

    if (typeof value === "string" || typeof value === "number") {
      classes.add(String(value));
      return;
    }

    if (Array.isArray(value)) {
      value.forEach(add);
      return;
    }

    for (const [name, enabled] of Object.entries(value)) {
      if (enabled) {
        classes.add(name);
      }
    }
  };

  inputs.forEach(add);
  return [...classes].join(" ");
}
