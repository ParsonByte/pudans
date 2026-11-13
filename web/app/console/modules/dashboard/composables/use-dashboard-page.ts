import { useTranslation } from "react-i18next";

export function useDashboardPage() {
  const { t } = useTranslation("console");
  return { description: t("overviewDescription") };
}
