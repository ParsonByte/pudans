import { useTranslation } from "react-i18next";
import { Alert, FormItem, Select, SettingsPanel } from "@pudans/components";
import { useConfigForm, type SystemConfig } from "../hooks/use-system-config";
import { isAssignableRole, useAssignableRoles } from "../hooks/use-assignable-roles";
import { ConfigFailed, SettingsLoading } from "./config-state";
import { TagInput } from "./tag-input";

interface UserValues {
  defaultRole: string;
  protectedUsernames: string[];
}

function stringOf(config: SystemConfig, key: string): string {
  const value = config[key];
  return typeof value === "string" ? value : "";
}

function parseUser(config: SystemConfig): UserValues {
  const defaultRole = stringOf(config, "defaultRole");
  return {
    defaultRole: isAssignableRole(defaultRole) ? defaultRole : "",
    protectedUsernames: stringOf(config, "protectedUsernames")
      .split(",")
      .map((name) => name.trim())
      .filter(Boolean),
  };
}

function mergeUser(config: SystemConfig, values: UserValues): SystemConfig {
  const next = { ...config };
  delete next.allowRegistration;
  delete next.mustVerifyEmailOnRegistration;
  next.defaultRole = values.defaultRole;
  next.protectedUsernames = values.protectedUsernames.join(",");
  return next;
}

export function UserSettings() {
  const { t } = useTranslation("console");
  const form = useConfigForm({ group: "user", parse: parseUser, merge: mergeUser });
  const roles = useAssignableRoles();

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
      <Alert severity="info">{t("settingsUserNoRegistration")}</Alert>

      <FormItem
        label={t("settingsDefaultRole")}
        description={t("settingsDefaultRoleHelp")}
        htmlFor="settings-user-default-role"
      >
        <Select
          id="settings-user-default-role"
          options={[
            { value: "", label: t("settingsDefaultRoleNone") },
            ...roles.map((role) => ({ value: role.value, label: role.label })),
          ]}
          displayEmpty
          value={values.defaultRole}
          onChange={(event) =>
            form.setValues({ ...values, defaultRole: String(event.target.value) })
          }
        />
      </FormItem>

      <FormItem
        label={t("settingsProtectedUsernames")}
        description={t("settingsProtectedUsernamesHelp")}
        htmlFor="settings-user-protected-usernames"
      >
        <TagInput
          id="settings-user-protected-usernames"
          values={values.protectedUsernames}
          placeholder={t("settingsProtectedUsernamesPlaceholder")}
          onChange={(next) => form.setValues({ ...values, protectedUsernames: next })}
        />
      </FormItem>
    </SettingsPanel>
  );
}
