import { useTranslation } from "react-i18next";

export function useUsersPage() {
  const { t } = useTranslation("console");
  return { description: t("usersDescription") };
}
