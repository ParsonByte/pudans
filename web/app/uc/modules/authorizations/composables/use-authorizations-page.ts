import { useTranslation } from "react-i18next";

export function useAuthorizationsPage() {
  const { t } = useTranslation("uc");
  return { description: t("authorizationsDescription") };
}
