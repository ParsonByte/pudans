import { useTranslation } from "react-i18next";

export function useSetupLocale(): "zh-CN" | "en" {
  const { i18n } = useTranslation("setup");
  return i18n.language.startsWith("zh") ? "zh-CN" : "en";
}
