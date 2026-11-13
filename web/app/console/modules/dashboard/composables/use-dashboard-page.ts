import { useTranslation } from "react-i18next";
import { useSession } from "../../../../../src/session/use-session";

export function useDashboardPage() {
  const { t } = useTranslation("console");
  const { session, permissions, hasPermission } = useSession();
  return { description: t("overviewDescription"), session, permissions, hasPermission };
}
