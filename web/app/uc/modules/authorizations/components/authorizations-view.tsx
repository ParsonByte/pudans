import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Avatar,
  Button as MuiButton,
  CircularProgress,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import { ConfirmDialog, FormItem, Icon } from "@pudans/components";
import { coreApiClient, type UserConnection } from "@pudans/api-client";
import { UcSettingsPage } from "../../../components/settings-page";

function formatTimestamp(value?: string): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

const PASSKEY_REGISTRATION_ID = "passkey";

/** Reference external-app grant list from SecuritySetting, fed by the UC
 * identity-connection API. Passkeys are filtered out: they are shown on the
 * security page and must not be removed through the identity unbind endpoint. */
export function UcAuthorizationsView() {
  const { t } = useTranslation("uc");
  const [connections, setConnections] = useState<UserConnection[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [feedback, setFeedback] = useState<{ severity: "success" | "error"; message: string }>();
  const [unbindTarget, setUnbindTarget] = useState<UserConnection>();
  const [unbinding, setUnbinding] = useState(false);

  useEffect(() => {
    let active = true;
    coreApiClient.uc.connection
      .listMyConnections()
      .then((response) => {
        if (active) {
          // The generated client still declares UserConnectionList; the
          // endpoint returns a bare array that also contains passkeys.
          const items = (response.data as unknown as UserConnection[]) ?? [];
          setConnections(
            items.filter((connection) => connection.spec?.registrationId !== PASSKEY_REGISTRATION_ID),
          );
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

  const confirmUnbind = (): void => {
    if (!unbindTarget) {
      return;
    }
    const name = unbindTarget.metadata.name;
    setUnbinding(true);
    coreApiClient.uc.connection
      .unbindConnection({ name })
      .then(() => {
        setConnections((previous) => previous.filter((connection) => connection.metadata.name !== name));
        setFeedback({ severity: "success", message: t("authorizationsUnbound") });
        setUnbindTarget(undefined);
      })
      .catch(() => {
        setFeedback({ severity: "error", message: t("authorizationsUnbindFailed") });
      })
      .finally(() => {
        setUnbinding(false);
      });
  };

  return (
    <UcSettingsPage>
      <Stack spacing={4} sx={{ width: { xs: "100%", md: "66.6667%" }, maxWidth: 768 }}>
        <FormItem label={t("authorizationsConnectionsTitle")}>
          {feedback ? <Alert severity={feedback.severity} sx={{ mb: 1 }}>{feedback.message}</Alert> : null}
          {failed ? (
            <Typography variant="body2" color="text.secondary">
              {t("authorizationsLoadError")}
            </Typography>
          ) : loading ? (
            <CircularProgress size={20} />
          ) : connections.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              {t("authorizationsConnectionsEmpty")}
            </Typography>
          ) : (
            <List disablePadding>
              {connections.map((connection) => (
                <ListItem
                  key={connection.metadata.name}
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
                      <Icon name="mdi:key-variant" size={20} />
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={
                      connection.spec.displayName ||
                      connection.spec.providerUserId ||
                      connection.metadata.name
                    }
                    secondary={`${connection.spec.providerUserId || "-"} · ${t(
                      "authorizationsUpdatedAt",
                    )} ${formatTimestamp(connection.spec.updatedAt)}`}
                    slotProps={{
                      primary: { variant: "body2", sx: { fontWeight: 500 }, noWrap: true },
                      secondary: {
                        variant: "caption",
                        noWrap: true,
                        sx: { overflow: "hidden", textOverflow: "ellipsis" },
                      },
                    }}
                  />
                  <MuiButton
                    variant="outlined"
                    color="error"
                    startIcon={<Icon name="mdi:close-outline" size={18} />}
                    onClick={() => setUnbindTarget(connection)}
                    sx={{ ml: 2, flex: "0 0 auto" }}
                  >
                    {t("authorizationsUnbind")}
                  </MuiButton>
                </ListItem>
              ))}
            </List>
          )}
        </FormItem>

        <ConfirmDialog
          open={Boolean(unbindTarget)}
          title={t("authorizationsUnbind")}
          description={t("authorizationsUnbindConfirm")}
          confirmLabel={t("authorizationsUnbind")}
          cancelLabel={t("cancel")}
          tone="danger"
          loading={unbinding}
          onConfirm={confirmUnbind}
          onCancel={() => setUnbindTarget(undefined)}
        />
      </Stack>
    </UcSettingsPage>
  );
}
