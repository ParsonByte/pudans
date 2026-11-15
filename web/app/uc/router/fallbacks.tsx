import { useTranslation } from "react-i18next";
import { Box, Typography } from "@mui/material";
import { Button, Icon, PageContainer, PageHeader, SectionCard } from "@pudans/components";
import { navigateToApp } from "../../../src/hooks/use-auth-navigation";

export function UcNotFoundPage() {
  const { t } = useTranslation("uc");
  return (
    <PageContainer maxWidth="lg">
      <PageHeader title={t("notFoundTitle")} />
      <SectionCard>
        <Box
          role="alert"
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "flex-start",
            gap: 2,
            py: 2,
          }}
        >
          <Icon name="mdi:alert-circle-outline" size={32} />
          <Typography variant="body2" sx={{ color: "text.secondary", lineHeight: 1.7 }}>
            {t("notFoundDescription")}
          </Typography>
          <Button type="button" variant="primary" onClick={() => navigateToApp("/uc")}>
            {t("backHome")}
          </Button>
        </Box>
      </SectionCard>
    </PageContainer>
  );
}

export function UcForbiddenPage() {
  return <UcNotFoundPage />;
}
