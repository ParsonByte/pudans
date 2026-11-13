import { useTranslation } from "react-i18next";

export function useProfilePage() {
  const { t } = useTranslation("uc");
  return { title: t("profileTitle"), description: t("profileDescription") };
}
