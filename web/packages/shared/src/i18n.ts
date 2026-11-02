import type { InitOptions, Resource } from "i18next";

export interface I18nConfigOptions {
  resources: Resource;
  lng?: string;
  fallbackLng?: string | string[];
  debug?: boolean;
  defaultNS?: string;
  ns?: string[];
}

/**
 * Shared i18next baseline. Apps add react-i18next before calling init() with
 * this config so component labels and app copy share the same namespaces.
 */
export function createI18nConfig({
  resources,
  lng = "en-US",
  fallbackLng = "en-US",
  debug = false,
  defaultNS = "common",
  ns = ["common"],
}: I18nConfigOptions): InitOptions {
  return {
    resources,
    lng,
    fallbackLng,
    debug,
    defaultNS,
    ns,
    initAsync: false,
    returnNull: false,
    interpolation: {
      escapeValue: false,
    },
    react: {
      useSuspense: false,
    },
  };
}
