import { useTranslation } from "react-i18next";
import { Icon } from "@pudans/components";
import styles from "./config-state.module.css";

export function ConfigFailed() {
  const { t } = useTranslation("console");
  return (
    <div className={styles.state}>
      <span className={styles.stateIcon} aria-hidden="true">
        <Icon name="mdi:alert-circle-outline" size={24} />
      </span>
      <div>
        <p className={styles.stateTitle}>{t("settingsLoadFailedTitle")}</p>
        <p className={styles.stateText}>{t("settingsLoadFailedDescription")}</p>
      </div>
    </div>
  );
}

export function SettingsLoading() {
  return (
    <div className={styles.loading} aria-hidden="true">
      {[0, 1, 2, 3].map((index) => (
        <span key={index} className={styles.skeleton} />
      ))}
    </div>
  );
}
