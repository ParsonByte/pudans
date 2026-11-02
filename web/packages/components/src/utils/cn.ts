import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merges conditional classes and resolves conflicts between Tailwind utilities.
 * CSS Module class names are left untouched because tailwind-merge ignores
 * non-utility identifiers.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
