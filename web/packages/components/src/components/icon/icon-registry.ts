import { addCollection, addIcon, type IconifyIcon, type IconifyJSON } from "@iconify/react/offline";

const registeredIcons = new Set<string>();

function iconName(prefix: string, name: string): string {
  return prefix ? `${prefix}:${name}` : name;
}

/** Registers one icon and tracks it for synchronous offline rendering. */
export function registerIcon(name: string, data: IconifyIcon): void {
  addIcon(name, data);
  registeredIcons.add(name);
}

/**
 * Registers an Iconify collection. Registered icons render synchronously (also
 * during SSR); unknown icons fall back to the Iconify API component in browsers.
 */
export function registerCollection(collection: IconifyJSON, prefix: string | boolean = true): void {
  addCollection(collection, prefix);
  const resolvedPrefix =
    prefix === false ? "" : typeof prefix === "string" ? prefix : collection.prefix;
  for (const name of Object.keys(collection.icons)) {
    registeredIcons.add(iconName(resolvedPrefix, name));
  }
}

export function isRegisteredIcon(name: string): boolean {
  return registeredIcons.has(name);
}
