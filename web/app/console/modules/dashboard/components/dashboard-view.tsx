import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { Avatar, Box, Chip, Stack, Typography } from "@mui/material";
import { Button, Icon } from "@pudans/components";
import { ContentCard, DescriptionRow } from "../../../../../src/layouts/content-card";
import { useDashboardPage } from "../composables/use-dashboard-page";

export function ConsoleDashboardView() {
  const { t } = useTranslation("console");
  const navigate = useNavigate();
  const { description, session, permissions, hasPermission } = useDashboardPage();
  const displayName = session?.displayName || session?.username || t("administrator");
  const username = session?.username || "-";
  const allPermissions = permissions.includes("*");
  const visiblePermissions = permissions.slice(0, 8);
  const remaining = Math.max(0, permissions.length - visiblePermissions.length);

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
          title={t("overviewIdentityTitle")}
          description={description}
          icon="mdi:view-dashboard-outline"
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
            </Box>
          </Stack>
        </ContentCard>
      </Box>

      <ContentCard
        title={t("overviewAccessTitle")}
        description={t("overviewAccessDescription")}
        icon="mdi:shield-account-outline"
      >
        {allPermissions ? (
          <Chip color="primary" variant="outlined" label={t("overviewPermissionAll")} />
        ) : visiblePermissions.length > 0 ? (
          <Stack direction="row" useFlexGap spacing={1} sx={{ flexWrap: "wrap" }}>
            {visiblePermissions.map((permission) => (
              <Chip key={permission} size="small" variant="outlined" label={permission} />
            ))}
            {remaining > 0 ? <Chip size="small" variant="outlined" label={`+${remaining}`} /> : null}
          </Stack>
        ) : (
          <Typography sx={{ color: "text.secondary", fontSize: "0.875rem" }}>
            {t("overviewNoPermissions")}
          </Typography>
        )}
        <Box sx={{ mt: 2 }}>
          <DescriptionRow label={t("usernameLabel")} value={username} />
        </Box>
      </ContentCard>

      <ContentCard
        title={t("overviewShortcutsTitle")}
        description={t("overviewShortcutsDescription")}
        icon="mdi:view-dashboard-outline"
      >
        <Stack spacing={1.25}>
          {hasPermission("users:manage") ? (
            <Button
              type="button"
              variant="outline"
              block
              startIcon={<Icon name="mdi:account-group-outline" size={18} />}
              onClick={() => navigate("/console/users")}
            >
              {t("navUsers")}
            </Button>
          ) : null}
          {hasPermission("audit:view") ? (
            <Button
              type="button"
              variant="outline"
              block
              startIcon={<Icon name="mdi:file-document-outline" size={18} />}
              onClick={() => navigate("/console/audit")}
            >
              {t("navAudit")}
            </Button>
          ) : null}
          <Button
            type="button"
            variant="ghost"
            block
            startIcon={<Icon name="mdi:account-circle-outline" size={18} />}
            onClick={() => navigate("/uc/profile")}
          >
            {t("backToUc")}
          </Button>
        </Stack>
      </ContentCard>
    </Box>
  );
}
