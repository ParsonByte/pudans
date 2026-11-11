import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Box, Button, Chip, Divider, Stack, Typography } from "@mui/material";
import { QRCodeSVG } from "qrcode.react";
import { Icon, OtpInput, useToast } from "@pudans/components";
import { PageContainer } from "@pudans/layouts";
import { useAuth } from "@web/providers/auth-provider";
import { UcPanel } from "../../components/uc-panel";

function randomSecret(): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const bytes = crypto.getRandomValues(new Uint8Array(20));
  return Array.from(bytes)
    .map((byte) => alphabet[byte % alphabet.length])
    .join("");
}

export function SecurityView() {
  const { t } = useTranslation("uc");
  const toast = useToast();
  const { user } = useAuth();
  const [otp, setOtp] = useState("");
  const [enabled, setEnabled] = useState(false);
  const secret = useMemo(randomSecret, []);

  const otpauth = `otpauth://totp/PUDANS:${encodeURIComponent(
    user?.name || "user",
  )}?secret=${secret}&issuer=PUDANS&algorithm=SHA1&digits=6&period=30`;

  const verify = (): void => {
    if (otp.length !== 6) {
      toast.error(t("securityOtpIncomplete"));
      return;
    }
    setEnabled(true);
    toast.success(t("securityEnabled"));
  };

  return (
    <PageContainer title={t("securityTitle")}>
      <UcPanel>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 1 }}>
          <Icon name="mdi:shield-lock-outline" size={22} />
          <Typography variant="h6">{t("securityTwoFactor")}</Typography>
          <Chip
            size="small"
            color={enabled ? "success" : "default"}
            label={enabled ? t("securityOn") : t("securityOff")}
          />
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t("securityTwoFactorHelp")}
        </Typography>

        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={3}
          sx={{ alignItems: { sm: "center" } }}
        >
          <Box
            sx={{
              p: 2,
              borderRadius: 3,
              border: "1px solid",
              borderColor: "divider",
              backgroundColor: "rgb(var(--pd-qr-surface))",
              display: "inline-grid",
              placeItems: "center",
            }}
          >
            <QRCodeSVG value={otpauth} size={168} level="M" includeMargin />
          </Box>
          <Stack spacing={1.5} sx={{ minWidth: 280, flex: 1 }}>
            <Typography variant="body2">{t("securityScanHint")}</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "monospace" }}>
              {secret.match(/.{1,4}/g)?.join(" ")}
            </Typography>
            <Divider />
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {t("securityEnterCode")}
            </Typography>
            <OtpInput
              value={otp}
              onChange={setOtp}
              length={6}
              size="md"
              helperText={t("securityOtpHelp")}
            />
            <Stack direction="row" spacing={1}>
              <Button variant="contained" size="small" onClick={verify}>
                {t("securityVerify")}
              </Button>
              <Button
                variant="outlined"
                size="small"
                onClick={() => {
                  setOtp("");
                  setEnabled(false);
                }}
              >
                {t("securityReset")}
              </Button>
            </Stack>
          </Stack>
        </Stack>

        {enabled ? (
          <Alert severity="success" sx={{ mt: 2 }}>
            {t("securityEnabledHint")}
          </Alert>
        ) : null}
      </UcPanel>

      <Box sx={{ mt: 2 }}>
        <UcPanel>
          <Typography variant="h6" sx={{ mb: 1 }}>
            {t("securitySessionsTitle")}
          </Typography>
          <Alert severity="info">{t("securitySessionsHint")}</Alert>
        </UcPanel>
      </Box>
    </PageContainer>
  );
}
