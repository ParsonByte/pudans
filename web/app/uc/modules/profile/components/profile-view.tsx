import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { Avatar, Box, Stack, Typography } from "@mui/material";
import { Button, Icon } from "@pudans/components";
import { ContentCard, DescriptionRow } from "../../../../../src/layouts/content-card";
import { useProfilePage } from "../composables/use-profile-page";

export function UcProfileView() {
  const { t } = useTranslation("uc");
  const navigate = useNavigate();
  const { description, session, permissions } = useProfilePage();
  const displayName = session?.displayName || session?.username || t("account");
  const username = session?.username || "-";
  const allPermissions = permissions.includes("*");

  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr", lg: "repeat(2, minmax(0, 1fr))" },
        gap: 3,
      }}
    >
      <Box sx={{ gridColumn: { xs: "auto", lg: "1 / -1" } }}>
        <ContentCard
          title={t("profileIdentityTitle")}
          description={t("profileIdentityDescription")}
          icon="mdi:account-circle-outline"
        >
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2.5} sx={{ alignItems: { xs: "flex-start", sm: "center" } }}>
            <Avatar
              src={session?.avatar || undefined}
              sx={{
                width: 68,
                height: 68,
                backgroundColor: "primary.main",
                color: "primary.contrastText",
                fontSize: "1.5rem",
                fontWeight: 600,
              }}
            >
              {displayName.slice(0, 1).toUpperCase()}
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="h5" sx={{ fontWeight: 600 }} noWrap>
                {displayName}
              </Typography>
              <Typography sx={{ mt: 0.5, color: "text.secondary", fontSize: "0.875rem" }} noWrap>
                @{username}
              </Typography>
              <Typography sx={{ mt: 1, color: "text.secondary", fontSize: "0.875rem" }}>
                {description}
              </Typography>
            </Box>
          </Stack>
        </ContentCard>
      </Box>

      <ContentCard
        title={t("profileAccountTitle")}
        description={t("profileAccountDescription")}
        icon="mdi:account-outline"
      >
        <DescriptionRow label={t("profileDisplayName")} value={displayName} />
        <DescriptionRow label={t("profileUsername")} value={username} />
        <DescriptionRow
          label={t("profilePermissions")}
          value={allPermissions ? t("profileAllPermissions") : String(permissions.length)}
        />
      </ContentCard>

      <ContentCard
        title={t("profileSecurityTitle")}
        description={t("profileSecurityDescription")}
        icon="mdi:shield-lock-outline"
      >
        <Stack spacing={1.25}>
          <Button
            type="button"
            variant="outline"
            block
            startIcon={<Icon name="mdi:shield-lock-outline" size={18} />}
            onClick={() => navigate("/uc/security")}
          >
            {t("profileManageSecurity")}
          </Button>
          <Button
            type="button"
            variant="outline"
            block
            startIcon={<Icon name="mdi:key-variant" size={18} />}
            onClick={() => navigate("/uc/authorizations")}
          >
            {t("profileManageAuthorizations")}
          </Button>
        </Stack>
      </ContentCard>
    </Box>
  );
}
