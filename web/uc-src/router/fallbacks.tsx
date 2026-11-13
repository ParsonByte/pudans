import { useTranslation } from "react-i18next";
import { Box, Typography } from "@mui/material";
import { Button, Icon } from "@pudans/components";
import { navigateToApp } from "../../src/hooks/use-auth-navigation";

export function UcNotFoundPage() {
  const { t } = useTranslation("uc");
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
      <Icon name="mdi:alert-circle-outline" size={28} />
      <Typography component="h1" sx={{ fontWeight: 600, fontSize: "1.375rem" }}>
        {t("notFoundTitle")}
      </Typography>
      <Typography sx={{ color: "text.secondary", fontSize: "0.875rem", lineHeight: 1.7 }}>
        {t("notFoundDescription")}
      </Typography>
      <Button type="button" onClick={() => navigateToApp("/uc")}>
        {t("backHome")}
      </Button>
    </Box>
  );
}

export function UcForbiddenPage() {
  return <UcNotFoundPage />;
}
