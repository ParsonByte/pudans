import { useTranslation } from "react-i18next";
import { Box, Typography } from "@mui/material";
import { Button, Icon } from "@pudans/components";
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
    <Box
      role="alert"
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: 1.5,
        maxWidth: 560,
        mt: { xs: 4, md: 8 },
        p: { xs: 2.5, md: 4 },
        border: 1,
        borderColor: "divider",
        borderRadius: 3,
        backgroundColor: "background.paper",
      }}
    >
      <Icon name={icon} size={28} />
      <Typography component="h1" sx={{ fontWeight: 600, fontSize: "1.375rem" }}>
        {title}
      </Typography>
      <Typography sx={{ color: "text.secondary", fontSize: "0.875rem", lineHeight: 1.7 }}>
        {description}
      </Typography>
      <Button type="button" onClick={() => navigateToApp(action.href)}>
        {action.label}
      </Button>
    </Box>
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
