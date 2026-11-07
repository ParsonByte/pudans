import { useTranslation } from "react-i18next";
import { PageContainer } from "@pudans/components";
import { UcPanel } from "../../components/uc-panel";

export function AuthorizationsView() {
  const { t } = useTranslation("uc");

  return (
    <PageContainer title={t("authorizationsTitle")}>
      <UcPanel>{t("authorizationsBody")}</UcPanel>
    </PageContainer>
  );
}
