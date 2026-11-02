export const PUDANS_UI_PLUGIN_API_VERSION = "v1alpha1" as const;

export interface PudansUiPluginAuthor {
  name: string;
  email?: string;
  url?: string;
}

export interface PudansUiPluginSpec {
  apiVersion: typeof PUDANS_UI_PLUGIN_API_VERSION;
  /** JavaScript entry, relative to the plugin root. */
  entry: string;
  /** Optional extracted stylesheet, relative to the plugin root. */
  styles?: string;
  minHostVersion?: string;
  permissions?: string[];
  /** Named extension points mapped to exported component names. */
  slots?: Record<string, string>;
}

export interface PudansUiPluginManifest {
  name: string;
  version: string;
  displayName?: string;
  description?: string;
  author?: string | PudansUiPluginAuthor;
  license?: string;
  homepage?: string;
  repository?: string;
  keywords?: string[];
  spec: PudansUiPluginSpec;
}

const PLUGIN_NAME_RE = /^(?:@[a-z0-9][a-z0-9._-]*\/)?[a-z0-9][a-z0-9._-]*$/;
const SEMVER_RE = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;

/**
 * Validates and freezes a UI plugin manifest at module scope. Invalid manifests
 * fail while the plugin bundle is being built rather than at host runtime.
 */
export function defineUiPluginManifest<const T extends PudansUiPluginManifest>(
  manifest: T,
): Readonly<T> {
  if (!PLUGIN_NAME_RE.test(manifest.name)) {
    throw new TypeError(
      `Invalid plugin name "${manifest.name}". Use a lowercase package-style name.`,
    );
  }
  if (!SEMVER_RE.test(manifest.version)) {
    throw new TypeError(`Invalid plugin version "${manifest.version}". Expected semver.`);
  }
  if (manifest.spec.apiVersion !== PUDANS_UI_PLUGIN_API_VERSION) {
    throw new TypeError(
      `Unsupported plugin API version "${manifest.spec.apiVersion}". Expected ${PUDANS_UI_PLUGIN_API_VERSION}.`,
    );
  }
  if (!manifest.spec.entry.endsWith(".js")) {
    throw new TypeError(`Plugin entry must be a .js file, received "${manifest.spec.entry}".`);
  }
  if (manifest.spec.styles && !manifest.spec.styles.endsWith(".css")) {
    throw new TypeError(`Plugin styles must be a .css file, received "${manifest.spec.styles}".`);
  }

  return Object.freeze({
    ...manifest,
    spec: Object.freeze({
      ...manifest.spec,
      permissions: manifest.spec.permissions
        ? Object.freeze([...manifest.spec.permissions])
        : undefined,
      slots: manifest.spec.slots ? Object.freeze({ ...manifest.spec.slots }) : undefined,
    }),
  }) as Readonly<T>;
}

export function isPudansUiPluginManifest(value: unknown): value is PudansUiPluginManifest {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  const spec = record.spec as Record<string, unknown> | undefined;
  return (
    typeof record.name === "string" &&
    typeof record.version === "string" &&
    typeof spec === "object" &&
    spec !== null &&
    spec.apiVersion === PUDANS_UI_PLUGIN_API_VERSION &&
    typeof spec.entry === "string"
  );
}
