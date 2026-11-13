import { useTranslation } from "react-i18next";

export function useAuthorizationsPage() {
  const { t } = useTranslation("uc");
  return { title: t("authorizationsTitle"), description: t("authorizationsDescription") };
}
