import { useTranslation } from "react-i18next";
import { Box, Chip, Stack } from "@mui/material";
import Sketch from "@uiw/react-color-sketch";
import { hsvaToHex } from "@uiw/color-convert";
import { accentPresets, FormItem, Input, SettingsPanel, Switch } from "@pudans/components";
import { useAppDispatch, useAppSelector } from "@web/store/hooks";
import { setAccentColor } from "@web/store/app-slice";
import { useConfigForm, type SystemConfig } from "../hooks/use-system-config";
import { ConfigFailed, SettingsLoading } from "./config-state";

interface AppearanceValues {
  blockSpiders: boolean;
  keywords: string;
  description: string;
}

function stringOf(config: SystemConfig, key: string): string {
  const value = config[key];
  return typeof value === "string" ? value : "";
}

function parseAppearance(config: SystemConfig): AppearanceValues {
  return {
    blockSpiders: config.blockSpiders === true,
    keywords: stringOf(config, "keywords"),
    description: stringOf(config, "description"),
  };
}

function mergeAppearance(config: SystemConfig, values: AppearanceValues): SystemConfig {
  return { ...config, ...values };
}

export function AppearanceSettings() {
  const { t } = useTranslation("console");
  const dispatch = useAppDispatch();
  const accentColor = useAppSelector((state) => state.app.accentColor);
  const form = useConfigForm({
    group: "seo",
    parse: parseAppearance,
    merge: mergeAppearance,
  });

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
      <FormItem label={t("settingsAccentColor")} description={t("settingsAccentColorHelp")}>
        <Stack spacing={1.5}>
          <Sketch
            color={accentColor}
            onChange={(color) => dispatch(setAccentColor(hsvaToHex(color.hsva)))}
            style={{ width: 240 }}
          />
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
            {accentPresets.map((color) => (
              <Chip
                key={color}
                label={color}
                size="small"
                onClick={() => dispatch(setAccentColor(color))}
                sx={{
                  borderLeft: "12px solid " + color,
                  fontFamily: "var(--pd-font-mono)",
                }}
              />
            ))}
          </Stack>
          <Box sx={{ height: 44, borderRadius: 2, backgroundColor: accentColor, opacity: 0.9 }} />
        </Stack>
      </FormItem>
      <FormItem
        label={t("settingsSeoKeywords")}
        description={t("settingsSeoKeywordsHelp")}
        htmlFor="settings-seo-keywords"
      >
        <Input
          id="settings-seo-keywords"
          value={values.keywords}
          onChange={(event) => form.setValues({ ...values, keywords: event.target.value })}
        />
      </FormItem>
      <FormItem
        label={t("settingsSeoDescription")}
        description={t("settingsSeoDescriptionHelp")}
        htmlFor="settings-seo-description"
      >
        <Input
          id="settings-seo-description"
          multiline
          minRows={3}
          value={values.description}
          onChange={(event) => form.setValues({ ...values, description: event.target.value })}
        />
      </FormItem>
      <FormItem
        label={t("settingsSeoBlockSpiders")}
        description={t("settingsSeoBlockSpidersHelp")}
        inline
      >
        <Switch
          checked={values.blockSpiders}
          onChange={(event) => form.setValues({ ...values, blockSpiders: event.target.checked })}
        />
      </FormItem>
    </SettingsPanel>
  );
}
