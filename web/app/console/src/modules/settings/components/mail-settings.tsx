import { useTranslation } from "react-i18next";
import { FormItem, Input, SettingsPanel, Switch } from "@pudans/components";
import { useConfigForm, type SystemConfig } from "../hooks/use-system-config";
import { ConfigFailed, SettingsLoading } from "./config-state";

interface MailValues {
  host: string;
  port: string;
  username: string;
  password: string;
  from: string;
  ssl: boolean;
}

function stringOf(config: SystemConfig, key: string): string {
  const value = config[key];
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number") {
    return String(value);
  }
  return "";
}

function parseMail(config: SystemConfig): MailValues {
  return {
    host: stringOf(config, "host"),
    port: stringOf(config, "port"),
    username: stringOf(config, "username"),
    password: stringOf(config, "password"),
    from: stringOf(config, "from"),
    ssl: config.ssl === true,
  };
}

function mergeMail(config: SystemConfig, values: MailValues): SystemConfig {
  const port = Number.parseInt(values.port, 10);
  return {
    ...config,
    host: values.host,
    port: Number.isFinite(port) ? port : undefined,
    username: values.username,
    password: values.password,
    from: values.from,
    ssl: values.ssl,
  };
}

export function MailSettings() {
  const { t } = useTranslation("console");
  const form = useConfigForm({ group: "mail", parse: parseMail, merge: mergeMail });

  if (form.failed) {
    return <ConfigFailed />;
  }
  if (form.initial === null || form.values === null) {
    return <SettingsLoading />;
  }

  const values = form.values;
  const update = (key: keyof MailValues, value: string | boolean): void => {
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
      <FormItem label={t("settingsMailHost")} description={t("settingsMailHostHelp")} htmlFor="settings-mail-host">
        <Input
          id="settings-mail-host"
          placeholder="smtp.example.com"
          value={values.host}
          onChange={(event) => update("host", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsMailPort")} description={t("settingsMailPortHelp")} htmlFor="settings-mail-port">
        <Input
          id="settings-mail-port"
          placeholder="465"
          inputMode="numeric"
          value={values.port}
          onChange={(event) => update("port", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsMailUsername")} description={t("settingsMailUsernameHelp")} htmlFor="settings-mail-username">
        <Input
          id="settings-mail-username"
          autoComplete="off"
          value={values.username}
          onChange={(event) => update("username", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsMailPassword")} description={t("settingsMailPasswordHelp")} htmlFor="settings-mail-password">
        <Input
          id="settings-mail-password"
          type="password"
          autoComplete="new-password"
          value={values.password}
          onChange={(event) => update("password", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsMailFrom")} description={t("settingsMailFromHelp")} htmlFor="settings-mail-from">
        <Input
          id="settings-mail-from"
          placeholder="no-reply@example.com"
          value={values.from}
          onChange={(event) => update("from", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsMailSsl")} description={t("settingsMailSslHelp")} inline>
        <Switch
          checked={values.ssl}
          onChange={(event) => update("ssl", event.target.checked)}
        />
      </FormItem>
    </SettingsPanel>
  );
}
