import { useTranslation } from "react-i18next";
import { FormItem, Input, SettingsPanel } from "@pudans/components";
import { useConfigForm, type SystemConfig } from "../hooks/use-system-config";
import { ConfigFailed, SettingsLoading } from "./config-state";

interface QueueValues {
  host: string;
  port: string;
  password: string;
  database: string;
  concurrency: string;
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

function parseQueue(config: SystemConfig): QueueValues {
  return {
    host: stringOf(config, "host"),
    port: stringOf(config, "port") || "6379",
    password: stringOf(config, "password"),
    database: stringOf(config, "database") || "0",
    concurrency: stringOf(config, "concurrency") || "4",
  };
}

function mergeQueue(config: SystemConfig, values: QueueValues): SystemConfig {
  const port = Number.parseInt(values.port, 10);
  const database = Number.parseInt(values.database, 10);
  const concurrency = Number.parseInt(values.concurrency, 10);
  return {
    ...config,
    host: values.host,
    port: Number.isFinite(port) ? port : 6379,
    password: values.password,
    database: Number.isFinite(database) ? database : 0,
    concurrency: Number.isFinite(concurrency) ? concurrency : 4,
  };
}

export function QueueSettings() {
  const { t } = useTranslation("console");
  const form = useConfigForm({ group: "queue", parse: parseQueue, merge: mergeQueue });

  if (form.failed) {
    return <ConfigFailed />;
  }
  if (form.initial === null || form.values === null) {
    return <SettingsLoading />;
  }

  const values = form.values;
  const update = (key: keyof QueueValues, value: string): void => {
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
      <FormItem label={t("settingsQueueHost")} description={t("settingsQueueHostHelp")} htmlFor="settings-queue-host">
        <Input
          id="settings-queue-host"
          placeholder="127.0.0.1"
          value={values.host}
          onChange={(event) => update("host", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsQueuePort")} description={t("settingsQueuePortHelp")} htmlFor="settings-queue-port">
        <Input
          id="settings-queue-port"
          inputMode="numeric"
          value={values.port}
          onChange={(event) => update("port", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsQueuePassword")} description={t("settingsQueuePasswordHelp")} htmlFor="settings-queue-password">
        <Input
          id="settings-queue-password"
          type="password"
          autoComplete="new-password"
          value={values.password}
          onChange={(event) => update("password", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsQueueDatabase")} description={t("settingsQueueDatabaseHelp")} htmlFor="settings-queue-database">
        <Input
          id="settings-queue-database"
          inputMode="numeric"
          value={values.database}
          onChange={(event) => update("database", event.target.value)}
        />
      </FormItem>
      <FormItem label={t("settingsQueueConcurrency")} description={t("settingsQueueConcurrencyHelp")} htmlFor="settings-queue-concurrency">
        <Input
          id="settings-queue-concurrency"
          inputMode="numeric"
          value={values.concurrency}
          onChange={(event) => update("concurrency", event.target.value)}
        />
      </FormItem>
    </SettingsPanel>
  );
}
