import { useTranslation } from "react-i18next";

export function useSecurityPage() {
  const { t } = useTranslation("uc");
  return { title: t("securityTitle"), description: t("securityDescription") };
}
