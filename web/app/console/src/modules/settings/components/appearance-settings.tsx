import { useTranslation } from "react-i18next";
import { FormItem, Input, SettingsPanel, Switch } from "@pudans/components";
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
          onChange={(event) =>
            form.setValues({ ...values, blockSpiders: event.target.checked })
          }
        />
      </FormItem>
    </SettingsPanel>
  );
}
