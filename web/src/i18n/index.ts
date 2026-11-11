import { createInstance } from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import ChainedBackend from "i18next-chained-backend";
import HttpBackend from "i18next-http-backend";
import LocalStorageBackend from "i18next-localstorage-backend";
import { initReactI18next } from "react-i18next";
import { createI18nConfig, DEFAULT_LOCALE, SUPPORTED_LOCALES } from "@pudans/shared";
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

void i18n
  .use(LanguageDetector)
  .use(ChainedBackend)
  .use(initReactI18next)
  .init({
    ...config,
    supportedLngs: [...SUPPORTED_LOCALES],
    load: "currentOnly",
    partialBundledLanguages: true,
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: LOCALE_KEY,
      caches: ["localStorage"],
    },
    backend: {
      backends: [LocalStorageBackend, HttpBackend],
      backendOptions: [
        {
          expirationTime: 7 * 24 * 60 * 60 * 1000,
          defaultVersion: "v1",
        },
        {
          loadPath: "/locales/{{lng}}/{{ns}}.json",
        },
      ],
    },
    react: {
      useSuspense: false,
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
