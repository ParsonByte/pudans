import { useTranslation } from "react-i18next";
import { FormItem, Input, Select, SettingsPanel } from "@pudans/components";
import { useConfigForm, type SystemConfig } from "../hooks/use-system-config";
import { ConfigFailed, SettingsLoading } from "./config-state";

interface SiteValues {
  title: string;
  subtitle: string;
  externalUrl: string;
  logo: string;
  favicon: string;
  language: string;
}

function stringOf(config: SystemConfig, key: string): string {
  const value = config[key];
  return typeof value === "string" ? value : "";
}

function parseSite(config: SystemConfig): SiteValues {
  return {
    title: stringOf(config, "title"),
    subtitle: stringOf(config, "subtitle"),
    externalUrl: stringOf(config, "externalUrl"),
    logo: stringOf(config, "logo"),
    favicon: stringOf(config, "favicon"),
    language: stringOf(config, "language"),
  };
}

function mergeSite(config: SystemConfig, values: SiteValues): SystemConfig {
  return { ...config, ...values };
}

export function SiteSettings() {
  const { t } = useTranslation("console");
  const form = useConfigForm({ group: "basic", parse: parseSite, merge: mergeSite });

  if (form.failed) {
    return <ConfigFailed />;
  }
  if (form.initial === null || form.values === null) {
    return <SettingsLoading />;
  }

  const values = form.values;
  const update = (key: keyof SiteValues, value: string): void => {
    form.setValues({ ...values, [key]: value });
  };

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
      <FormItem label={t("settingsFieldTitle")} description={t("settingsFieldTitleHelp")} htmlFor="settings-site-title">
        <Input
          id="settings-site-title"
          value={values.title}
          onChange={(event) => update("title", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsFieldSubtitle")} description={t("settingsFieldSubtitleHelp")} htmlFor="settings-site-subtitle">
        <Input
          id="settings-site-subtitle"
          value={values.subtitle}
          onChange={(event) => update("subtitle", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsFieldUrl")} description={t("settingsFieldUrlHelp")} htmlFor="settings-site-url">
        <Input
          id="settings-site-url"
          placeholder="https://"
          value={values.externalUrl}
          onChange={(event) => update("externalUrl", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsFieldLogo")} description={t("settingsFieldLogoHelp")} htmlFor="settings-site-logo">
        <Input
          id="settings-site-logo"
          placeholder="https://"
          value={values.logo}
          onChange={(event) => update("logo", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsFieldFavicon")} description={t("settingsFieldFaviconHelp")} htmlFor="settings-site-favicon">
        <Input
          id="settings-site-favicon"
          placeholder="https://"
          value={values.favicon}
          onChange={(event) => update("favicon", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsFieldLanguage")} description={t("settingsFieldLanguageHelp")} htmlFor="settings-site-language">
        <Select
          id="settings-site-language"
          options={[
            { value: "", label: t("settingsLanguageDefault") },
            { value: "zh-CN", label: "简体中文" },
            { value: "en-US", label: "English" },
          ]}
          displayEmpty
          value={values.language}
          onChange={(event) => update("language", String(event.target.value))}
        />
      </FormItem>
    </SettingsPanel>
  );
}
