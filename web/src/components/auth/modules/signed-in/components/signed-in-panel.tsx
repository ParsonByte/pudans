import { useTranslation } from "react-i18next";
import { Avatar, Button, Icon } from "@pudans/components";
import type { SessionView } from "../../../../../util/api-types";
import styles from "./signed-in-panel.module.css";

export interface SignedInPanelProps {
  session: SessionView;
  signingOut: boolean;
  onSignOut: () => void;
}

export function SignedInPanel({ session, signingOut, onSignOut }: SignedInPanelProps) {
  const { t } = useTranslation("auth");
  const displayName = session.displayName || session.username || "";
  const initial = displayName.slice(0, 1).toUpperCase();

  return (
    <div className={styles.view}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>{t("doneEyebrow")}</span>
        <h2 className={styles.title}>{t("doneTitle")}</h2>
        <p className={styles.description}>{t("doneDescription")}</p>
      </div>

      <div className={styles.account} role="status">
        <Avatar
          className={styles.avatar}
          src={session.avatar || undefined}
          name={displayName}
          fallback={initial}
        />
        <div className={styles.identity}>
          <span className={styles.label}>{t("accountLabel")}</span>
          <strong className={styles.name}>{displayName}</strong>
          <span className={styles.username}>{session.username}</span>
        </div>
        <span className={styles.verified} aria-hidden="true">
          <Icon name="mdi:check-decagram-outline" size={22} />
        </span>
      </div>

      <Button
        type="button"
        size="lg"
        radius="lg"
        block
        variant="outline"
        loading={signingOut}
        loadingText={t("signingOut")}
        startIcon={<Icon name="mdi:logout" size={18} />}
        className={styles.submit}
        onClick={onSignOut}
      >
        {t("signOut")}
      </Button>
    </div>
  );
}
