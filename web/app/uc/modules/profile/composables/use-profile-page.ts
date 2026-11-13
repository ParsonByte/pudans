import { useTranslation } from "react-i18next";
import { useSession } from "../../../../../src/session/use-session";

export function useProfilePage() {
  const { t } = useTranslation("uc");
  const { session, permissions } = useSession();
  return { description: t("profileDescription"), session, permissions };
}
