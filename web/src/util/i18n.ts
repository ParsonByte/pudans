import { createInstance } from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import { createI18nConfig, DEFAULT_LOCALE, SUPPORTED_LOCALES } from "@pudans/utils";
import { resources } from "./i18n-resources";

export const LOCALE_STORAGE_KEY = "pudans:locale";

export function normalizeLocale(language: string | undefined): string {
  if (!language) {
    return DEFAULT_LOCALE;
  }
  return language.startsWith("zh") ? "zh-CN" : "en-US";
}

export const i18n = createInstance();

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    ...createI18nConfig({
      resources,
      lng: DEFAULT_LOCALE,
      fallbackLng: "en-US",
      ns: ["common", "auth"],
    }),
    supportedLngs: [...SUPPORTED_LOCALES],
    load: "currentOnly",
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: LOCALE_STORAGE_KEY,
      caches: ["localStorage"],
    },
  })
  .then(() => {
    document.documentElement.lang = normalizeLocale(i18n.language);
  })
  .catch((error: unknown) => {
    console.error("i18n initialization failed", error);
  });

i18n.on("languageChanged", (language) => {
  const locale = normalizeLocale(language);
  document.documentElement.lang = locale;
  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Storage can be blocked in private browsing; language switching still works.
  }
});

export default i18n;
