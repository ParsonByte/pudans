import { useTranslation } from "react-i18next";
import { Box, Typography } from "@mui/material";
import { Button, Icon, PageContainer, PageHeader, SectionCard } from "@pudans/components";
import { navigateToApp } from "../../../src/hooks/use-auth-navigation";

function FallbackCard({
  icon,
  title,
  description,
  action,
}: {
  icon: string;
  title: string;
  description: string;
  action: { label: string; href: string };
}) {
  return (
    <PageContainer maxWidth="xl">
      <PageHeader title={title} />
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
          <Icon name={icon} size={32} />
          <Typography variant="body2" sx={{ color: "text.secondary", lineHeight: 1.7 }}>
            {description}
          </Typography>
          <Button type="button" variant="primary" onClick={() => navigateToApp(action.href)}>
            {action.label}
          </Button>
        </Box>
      </SectionCard>
    </PageContainer>
  );
}

export function ConsoleForbiddenPage() {
  const { t } = useTranslation("common");
  return (
    <FallbackCard
      icon="mdi:shield-lock-outline"
      title={t("forbiddenTitle")}
      description={t("forbiddenDescription")}
      action={{ label: t("forbiddenBack"), href: "/uc" }}
    />
  );
}

export function ConsoleNotFoundPage() {
  const { t } = useTranslation("console");
  return (
    <FallbackCard
      icon="mdi:alert-circle-outline"
      title={t("notFoundTitle")}
      description={t("notFoundDescription")}
      action={{ label: t("backHome"), href: "/console" }}
    />
  );
}
