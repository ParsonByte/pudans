import { useTranslation } from "react-i18next";
import { PageContainer } from "@pudans/components";
import { UcPanel } from "../../components/uc-panel";

export function SecurityView() {
  const { t } = useTranslation("uc");

  return (
    <PageContainer title={t("securityTitle")}>
      <UcPanel>{t("securityBody")}</UcPanel>
    </PageContainer>
  );
}
