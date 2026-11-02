import { createInstance } from "i18next";
import { initReactI18next } from "react-i18next";
import { createI18nConfig, SUPPORTED_LOCALES } from "@pudans/shared";
import { resources } from "./resources";

const LOCALE_KEY = "pudans:locale";

function readLocale(): string {
  if (typeof window !== "undefined") {
    const stored = window.localStorage.getItem(LOCALE_KEY);
    if (stored && (SUPPORTED_LOCALES as readonly string[]).includes(stored)) {
      return stored;
    }
  }
  return typeof navigator !== "undefined" && navigator.language.startsWith("zh")
    ? "zh-CN"
    : "en-US";
}

const locale = readLocale();

export const i18n = createInstance();

void i18n.use(initReactI18next).init(
  createI18nConfig({
    resources,
    lng: locale,
    fallbackLng: "en-US",
    ns: ["common", "setup", "auth", "console", "uc"],
  }),
);

export default i18n;
i18n.on("languageChanged", (language) => {
  try {
    window.localStorage.setItem(LOCALE_KEY, language);
  } catch {
    // Private browsing or blocked storage must not break language switching.
  }
});
