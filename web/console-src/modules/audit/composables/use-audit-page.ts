import { useTranslation } from "react-i18next";

export function useAuditPage() {
  const { t } = useTranslation("console");
  return { title: t("auditTitle"), description: t("auditDescription") };
}
