import { createInstance } from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import { createI18nConfig, DEFAULT_LOCALE, SUPPORTED_LOCALES } from "@pudans/utils";
import { resources } from "./resources";

export const LOCALE_KEY = "pudans:locale";

/** Backend-loaded namespaces. Bundled namespaces live in resources.ts. */
export const LAZY_NAMESPACES: readonly string[] = [];

const bundledNamespaces = ["common", "setup", "auth", "console", "uc"] as const;

export function normalizeLocale(language: string | undefined): string {
  if (!language) {
    return DEFAULT_LOCALE;
  }
  if (language.startsWith("zh")) {
    return "zh-CN";
  }
  return "en-US";
}

const config = createI18nConfig({
  resources,
  lng: DEFAULT_LOCALE,
  fallbackLng: "en-US",
  ns: [...bundledNamespaces],
});

export const i18n = createInstance();

// All namespaces are bundled in resources.ts (LAZY_NAMESPACES is empty), so
// there is no runtime locale fetch. This keeps startup deterministic, removes
// 404 noise from deployments without /locales and works offline.
void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    ...config,
    supportedLngs: [...SUPPORTED_LOCALES],
    load: "currentOnly",
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: LOCALE_KEY,
      caches: ["localStorage"],
    },
  })
  .catch((error: unknown) => {
    console.error("i18n initialization failed", error);
  });

i18n.on("languageChanged", (language) => {
  const locale = normalizeLocale(language);
  try {
    window.localStorage.setItem(LOCALE_KEY, locale);
  } catch {
    // Private browsing or blocked storage must not break language switching.
  }
});

export default i18n;
