import { useTranslation } from "react-i18next";
import { Box, Typography } from "@mui/material";
import { Turnstile } from "@marsidev/react-turnstile";
import ReCAPTCHA from "react-google-recaptcha";
import { usePudansTheme } from "@pudans/components";
import { FormItem, Select, SettingsPanel, Switch } from "@pudans/components";
import { useConfigForm, type SystemConfig } from "../hooks/use-system-config";
import { ConfigFailed, SettingsLoading } from "./config-state";

interface CaptchaValues {
  enabled: boolean;
  provider: string;
}

function boolOf(config: SystemConfig, key: string): boolean {
  return config[key] === true;
}

function stringOf(config: SystemConfig, key: string): string {
  const value = config[key];
  return typeof value === "string" ? value : "";
}

function parseCaptcha(config: SystemConfig): CaptchaValues {
  return {
    enabled: boolOf(config, "enabled"),
    provider: stringOf(config, "provider"),
  };
}

function mergeCaptcha(config: SystemConfig, values: CaptchaValues): SystemConfig {
  return { ...config, enabled: values.enabled, provider: values.provider };
}

export function CaptchaSettings() {
  const { t } = useTranslation("console");
  const { resolvedMode } = usePudansTheme();
  const form = useConfigForm({ group: "captcha", parse: parseCaptcha, merge: mergeCaptcha });

  if (form.failed) {
    return <ConfigFailed />;
  }
  if (form.initial === null || form.values === null) {
    return <SettingsLoading />;
  }

  const values = form.values;

  return (
    <SettingsPanel
      initialValues={form.initial}
      values={values}
      onSave={form.save}
      onReset={form.reset}
      saving={form.saving}
      saveLabel={t("settingsSave")}
      savingLabel={t("settingsSaving")}
      resetLabel={t("settingsReset")}
      dirtyLabel={t("settingsUnsaved")}
    >
      <FormItem
        label={t("settingsCaptchaEnabled")}
        description={t("settingsCaptchaEnabledHelp")}
        inline
      >
        <Switch
          checked={values.enabled}
          onChange={(event) => form.setValues({ ...values, enabled: event.target.checked })}
        />
      </FormItem>

      {values.provider === "turnstile" || values.provider === "recaptcha" ? (
        <FormItem label={t("settingsCaptchaPreview")} description={t("settingsCaptchaPreviewHelp")}>
          <Box
            sx={{
              p: 2,
              border: "1px dashed",
              borderColor: "divider",
              borderRadius: 2,
              display: "grid",
              placeItems: "center",
              minHeight: 96,
            }}
          >
            {values.provider === "turnstile" ? (
              <Turnstile
                siteKey="1x00000000000000000000AA"
                options={{ theme: resolvedMode, size: "normal" }}
              />
            ) : (
              <ReCAPTCHA sitekey="6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI" theme={resolvedMode} />
            )}
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
              {t("settingsCaptchaPreviewNote")}
            </Typography>
          </Box>
        </FormItem>
      ) : null}

      <FormItem
        label={t("settingsCaptchaProvider")}
        description={t("settingsCaptchaProviderHelp")}
        htmlFor="settings-captcha-provider"
      >
        <Select
          id="settings-captcha-provider"
          options={[
            { value: "", label: t("settingsCaptchaProviderNone") },
            { value: "turnstile", label: "Cloudflare Turnstile" },
            { value: "recaptcha", label: "Google reCAPTCHA" },
            { value: "hcaptcha", label: "hCaptcha" },
            { value: "tencent", label: t("settingsCaptchaProviderTencent") },
          ]}
          displayEmpty
          value={values.provider}
          onChange={(event) => form.setValues({ ...values, provider: String(event.target.value) })}
        />
      </FormItem>
    </SettingsPanel>
  );
}
