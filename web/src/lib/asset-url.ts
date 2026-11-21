import { BASE_PATH } from "./base-path";

const ABSOLUTE_URL_PATTERN = /^[a-z]+:\/\//i;

export function assetUrl(path: string): string {
  if (ABSOLUTE_URL_PATTERN.test(path) || path.startsWith("data:")) {
    return path;
  }
  const normalized = path.replace(/^\/+/, "");
  const base = BASE_PATH.replace(/\/+$/, "");
  return `${base}/${normalized}`;
}
