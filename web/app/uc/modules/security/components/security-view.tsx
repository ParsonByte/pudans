import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Avatar,
  Box,
  Button as MuiButton,
  Chip,
  CircularProgress,
  Collapse,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import {
  Button,
  ConfirmDialog,
  FormItem,
  Icon,
  Input,
} from "@pudans/components";
import {
  coreApiClient,
  type TwoFactorAuthSettings,
  type UserConnection,
  type UserDevice,
} from "@pudans/api-client";
import { UcSettingsPage } from "../../../components/settings-page";

type Feedback = { severity: "success" | "error"; message: string } | undefined;

function formatTimestamp(value?: string): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** Reference SecuritySetting columns: password and two-factor are 5/12 of the
 * content width, passkeys and the remaining lists 8/12. */
function SettingsSection({ span, children }: { span: 5 | 8; children: ReactNode }) {
  return (
    <Box sx={{ width: { xs: "100%", md: `${(span / 12) * 100}%` }, minWidth: 0 }}>
      {children}
    </Box>
  );
}

/** Reference SecuritySetting, mapped onto the available UC APIs: password
 * change, two-factor status, signed-in devices and passkeys. */
export function UcSecurityView() {
  const { t } = useTranslation("uc");

  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [repeatPassword, setRepeatPassword] = useState("");
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordFeedback, setPasswordFeedback] = useState<Feedback>();

  const [twoFactor, setTwoFactor] = useState<TwoFactorAuthSettings>();
  const [twoFactorLoading, setTwoFactorLoading] = useState(true);

  const [devices, setDevices] = useState<UserDevice[]>([]);
  const [devicesLoading, setDevicesLoading] = useState(true);
  const [devicesFailed, setDevicesFailed] = useState(false);
  const [deviceFeedback, setDeviceFeedback] = useState<Feedback>();
  const [revokeTarget, setRevokeTarget] = useState<UserDevice>();
  const [revoking, setRevoking] = useState(false);

  const [passkeys, setPasskeys] = useState<UserConnection[]>([]);
  const [passkeysLoading, setPasskeysLoading] = useState(true);
  const [passkeysFailed, setPasskeysFailed] = useState(false);

  useEffect(() => {
    let active = true;
    coreApiClient.uc.twoFactor
      .getTwoFactorAuthenticationSettings()
      .then((response) => {
        if (active) {
          setTwoFactor(response.data);
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) {
          setTwoFactorLoading(false);
        }
      });
    coreApiClient.uc.device
      .listDevices()
      .then((response) => {
        if (active) {
          // The generated client still declares DeviceList; the endpoint
          // returns a bare UserDevice array.
          setDevices((response.data as unknown as UserDevice[]) ?? []);
        }
      })
      .catch(() => {
        if (active) {
          setDevicesFailed(true);
        }
      })
      .finally(() => {
        if (active) {
          setDevicesLoading(false);
        }
      });
    coreApiClient.uc.passkey
      .listPasskeys()
      .then((response) => {
        if (active) {
          setPasskeys(response.data ?? []);
        }
      })
      .catch(() => {
        if (active) {
          setPasskeysFailed(true);
        }
      })
      .finally(() => {
        if (active) {
          setPasskeysLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const submitPassword = (): void => {
    if (newPassword.length < 8) {
      setPasswordFeedback({ severity: "error", message: t("securityPasswordTooShort") });
      return;
    }
    if (newPassword !== repeatPassword) {
      setPasswordFeedback({ severity: "error", message: t("securityPasswordMismatch") });
      return;
    }
    setPasswordSaving(true);
    setPasswordFeedback(undefined);
    coreApiClient.uc.user
      .changeMyPassword1({
        changeMyPasswordRequest: { oldPassword: currentPassword, password: newPassword },
      })
      .then(() => {
        setPasswordFeedback({ severity: "success", message: t("securityPasswordChanged") });
        setCurrentPassword("");
        setNewPassword("");
        setRepeatPassword("");
        setShowPasswordForm(false);
      })
      .catch(() => {
        setPasswordFeedback({ severity: "error", message: t("securityPasswordFailed") });
      })
      .finally(() => {
        setPasswordSaving(false);
      });
  };

  const confirmRevoke = (): void => {
    if (!revokeTarget) {
      return;
    }
    const deviceId = revokeTarget.device.metadata.name;
    setRevoking(true);
    coreApiClient.uc.device
      .revokeDevice({ deviceId })
      .then(() => {
        setDevices((previous) =>
          previous.filter((device) => device.device.metadata.name !== deviceId),
        );
        setDeviceFeedback({ severity: "success", message: t("securityDeviceRevoked") });
        setRevokeTarget(undefined);
      })
      .catch(() => {
        setDeviceFeedback({ severity: "error", message: t("securityDeviceRevokeFailed") });
      })
      .finally(() => {
        setRevoking(false);
      });
  };

  return (
    <UcSettingsPage>
      <Stack spacing={4}>
        <SettingsSection span={5}>
          <FormItem
            label={t("securityPasswordTitle")}
            description={t("securityPasswordDescription")}
          >
            {!showPasswordForm ? (
              <Box sx={{ mt: 1 }}>
                <Button
                  variant="secondary"
                  startIcon={<Icon name="pd:Edit" size={20} />}
                  onClick={() => {
                    setPasswordFeedback(undefined);
                    setShowPasswordForm(true);
                  }}
                >
                  {t("securityChangePassword")}
                </Button>
              </Box>
            ) : null}
            <Collapse in={showPasswordForm} unmountOnExit>
              <Stack
                component="form"
                spacing={2}
                sx={{ mt: 2 }}
                onSubmit={(event) => {
                  event.preventDefault();
                  submitPassword();
                }}
              >
                <Input
                  size="sm"
                  type="password"
                  autoComplete="current-password"
                  label={t("securityCurrentPassword")}
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  inputProps={{ minLength: 4, maxLength: 64 }}
                  required
                />
                <Input
                  size="sm"
                  type="password"
                  autoComplete="new-password"
                  label={t("securityNewPassword")}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  inputProps={{ minLength: 6, maxLength: 128 }}
                  required
                />
                <Input
                  size="sm"
                  type="password"
                  autoComplete="new-password"
                  label={t("securityRepeatPassword")}
                  value={repeatPassword}
                  onChange={(event) => setRepeatPassword(event.target.value)}
                  inputProps={{ minLength: 6, maxLength: 128 }}
                  required
                />
                <Box>
                  <Button
                    type="submit"
                    variant="primary"
                    loading={passwordSaving}
                    loadingText={t("saving")}
                  >
                    {t("save")}
                  </Button>
                </Box>
              </Stack>
            </Collapse>
            {passwordFeedback ? (
              <Alert severity={passwordFeedback.severity} sx={{ mt: 2 }}>
                {passwordFeedback.message}
              </Alert>
            ) : null}
          </FormItem>
        </SettingsSection>

        <SettingsSection span={5}>
          <FormItem
            label={
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                {t("securityTwoFactorTitle")}
                {twoFactorLoading ? (
                  <CircularProgress size={14} />
                ) : twoFactor?.available === false ? null : (
                  <Chip
                    size="small"
                    color={twoFactor?.enabled ? "success" : "default"}
                    label={
                      twoFactor?.enabled ? t("securityTwoFactorOn") : t("securityTwoFactorOff")
                    }
                    sx={{ height: "auto", borderRadius: "8px", fontSize: "0.75rem" }}
                  />
                )}
              </Box>
            }
          >
            <Typography variant="caption" color="text.secondary">
              {twoFactor?.available === false ? t("securityTwoFactorUnavailable") : null}
            </Typography>
          </FormItem>
        </SettingsSection>

        <SettingsSection span={8}>
          <FormItem label={t("securityPasskeysTitle")}>
            {passkeysFailed ? (
              <Typography variant="body2" color="text.secondary">
                {t("securityPasskeysLoadError")}
              </Typography>
            ) : passkeysLoading ? (
              <CircularProgress size={20} />
            ) : passkeys.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t("securityPasskeysEmpty")}
              </Typography>
            ) : (
              <List disablePadding>
                {passkeys.map((passkey) => (
                  <ListItem
                    key={passkey.metadata.name}
                    sx={{
                      mt: 1,
                      py: 0.5,
                      border: 1,
                      borderColor: "divider",
                      borderRadius: 1,
                    }}
                  >
                    <ListItemAvatar>
                      <Avatar sx={{ backgroundColor: "primary.main", color: "primary.contrastText" }}>
                        <Icon name="pd:PersonPasskey" size={20} />
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={passkey.spec.displayName || passkey.metadata.name}
                      secondary={`${t("securityPasskeyAdded")} ${formatTimestamp(
                        passkey.spec.updatedAt,
                      )}`}
                      slotProps={{
                        primary: { variant: "body2", sx: { fontWeight: 500 } },
                        secondary: { variant: "caption" },
                      }}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </FormItem>
        </SettingsSection>

        <SettingsSection span={8}>
          <FormItem label={t("securityDevicesTitle")}>
            {deviceFeedback ? (
              <Alert severity={deviceFeedback.severity} sx={{ mb: 1 }}>
                {deviceFeedback.message}
              </Alert>
            ) : null}
            {devicesFailed ? (
              <Typography variant="body2" color="text.secondary">
                {t("securityDevicesLoadError")}
              </Typography>
            ) : devicesLoading ? (
              <CircularProgress size={20} />
            ) : devices.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t("securityDevicesEmpty")}
              </Typography>
            ) : (
              <List disablePadding>
                {devices.map((item) => (
                  <ListItem
                    key={item.device.metadata.name}
                    sx={{
                      mt: 1,
                      py: 0.5,
                      border: 1,
                      borderColor: "divider",
                      borderRadius: 1,
                      alignItems: "center",
                    }}
                  >
                    <ListItemAvatar>
                      <Avatar sx={{ backgroundColor: "primary.main", color: "primary.contrastText" }}>
                        <Icon name="mdi:laptop" size={20} />
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
                          <Typography variant="inherit" noWrap sx={{ minWidth: 0 }}>
                            {item.device.spec.userAgent || item.device.metadata.name}
                          </Typography>
                          {item.currentDevice ? (
                            <Chip
                              size="small"
                              color="success"
                              label={t("securityDeviceCurrent")}
                              sx={{ height: "auto", borderRadius: "8px", fontSize: "0.75rem" }}
                            />
                          ) : null}
                        </Box>
                      }
                      secondary={`${item.device.spec.ipAddress || "-"} · ${formatTimestamp(
                        item.device.spec.lastAccessedTime || item.device.spec.lastAuthenticatedTime,
                      )}`}
                      slotProps={{
                        primary: { variant: "body2" },
                        secondary: { variant: "caption", noWrap: true },
                      }}
                    />
                    <MuiButton
                      size="small"
                      variant="outlined"
                      color="error"
                      startIcon={<Icon name="mdi:close-outline" size={18} />}
                      onClick={() => setRevokeTarget(item)}
                      sx={{ ml: 2, flex: "0 0 auto" }}
                    >
                      {t("securityRevokeDevice")}
                    </MuiButton>
                  </ListItem>
                ))}
              </List>
            )}
          </FormItem>
        </SettingsSection>

        <ConfirmDialog
          open={Boolean(revokeTarget)}
          title={t("securityRevokeDevice")}
          description={t("securityRevokeDeviceConfirm")}
          confirmLabel={t("revoke")}
          cancelLabel={t("cancel")}
          tone="danger"
          loading={revoking}
          onConfirm={confirmRevoke}
          onCancel={() => setRevokeTarget(undefined)}
        />
      </Stack>
    </UcSettingsPage>
  );
}
