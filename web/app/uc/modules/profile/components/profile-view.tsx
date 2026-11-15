import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Box, CircularProgress, Stack, Typography } from "@mui/material";
import { FormItem, Input, UserAvatar } from "@pudans/components";
import { coreApiClient, type UserView } from "@pudans/api-client";
import { useProfilePage } from "../composables/use-profile-page";
import { UcSettingsPage } from "../../../components/settings-page";

function formatTimestamp(value?: string): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** Reference ProfileSetting: avatar column on the right (row-reverse) and the
 * identity fields on the left, each under a SettingForm-style label. */
export function UcProfileView() {
  const { t } = useTranslation("uc");
  const { session, permissions } = useProfilePage();
  const [detail, setDetail] = useState<UserView>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    coreApiClient.uc.user
      .getMyUserDetail()
      .then((response) => {
        if (active) {
          setDetail(response.data);
        }
      })
      .catch(() => {
        if (active) {
          setFailed(true);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const spec = detail?.spec;
  const displayName = spec?.displayName || session?.displayName || session?.username || "-";
  const email = spec?.email || "-";
  const userId = spec?.uid || detail?.metadata?.name || "-";
  const allPermissions = permissions.includes("*");

  return (
    <UcSettingsPage>
      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", py: 20 }}>
          <CircularProgress size={28} />
        </Box>
      ) : (
        <Stack spacing={3}>
          {failed ? <Alert severity="warning">{t("profileLoadError")}</Alert> : null}
          <Box
            sx={{
              display: "flex",
              flexDirection: { xs: "column", md: "row-reverse" },
              gap: { xs: 3, md: 4 },
              alignItems: "flex-start",
              width: "100%",
            }}
          >
            <Box sx={{ width: { xs: "100%", md: "50%" }, minWidth: 0 }}>
              <FormItem label={t("profileAvatar")}>
                <UserAvatar
                  name={displayName}
                  src={spec?.avatar || session?.avatar || undefined}
                  size={200}
                  square
                />
              </FormItem>
            </Box>

            <Box sx={{ width: { xs: "100%", md: "50%" }, minWidth: 0 }}>
              <Stack spacing={3}>
                <FormItem label={t("profileEmail")}>
                  <Input size="sm" value={email} disabled fullWidth />
                </FormItem>
                <FormItem label={t("profileDisplayName")}>
                  <Input size="sm" value={displayName} disabled fullWidth />
                </FormItem>
                <Box sx={{ display: "flex", gap: 4 }}>
                  <Box sx={{ width: "50%", minWidth: 0 }}>
                    <FormItem label={t("profileUid")}>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {userId}
                      </Typography>
                    </FormItem>
                  </Box>
                  <Box sx={{ width: "50%", minWidth: 0 }}>
                    <FormItem label={t("profileRegisteredAt")}>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {formatTimestamp(spec?.registeredAt)}
                      </Typography>
                    </FormItem>
                  </Box>
                </Box>
                <FormItem label={t("profilePermissions")}>
                  <Typography variant="body2" color="text.secondary">
                    {allPermissions
                      ? t("profileAllPermissions")
                      : t("profilePermissionCount", { count: permissions.length })}
                  </Typography>
                </FormItem>
              </Stack>
            </Box>
          </Box>
        </Stack>
      )}
    </UcSettingsPage>
  );
}
