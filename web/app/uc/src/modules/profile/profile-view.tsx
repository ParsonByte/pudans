import { useTranslation } from "react-i18next";
import { PageContainer } from "@pudans/components";
import { UcPanel } from "../../components/uc-panel";

export function ProfileView() {
  const { t } = useTranslation("uc");

  return (
    <PageContainer title={t("profileTitle")}>
      <UcPanel>{t("profileBody")}</UcPanel>
    </PageContainer>
  );
}
