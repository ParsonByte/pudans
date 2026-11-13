import { useTranslation } from "react-i18next";
import { Avatar, Button, Icon } from "@pudans/components";
import type { SessionView } from "@pudans/api-client";
import { BRAND_MARK, BRAND_WORDMARK } from "../../../../../constants";
import type { SignedInDestination } from "../composables/use-signed-in-destinations";
import styles from "./signed-in-panel.module.css";

export interface SignedInPanelProps {
  session: SessionView;
  destinations: readonly SignedInDestination[];
  signingOut: boolean;
  onNavigate: (href: string) => void;
  onSignOut: () => void;
}

export function SignedInPanel({
  session,
  destinations,
  signingOut,
  onNavigate,
  onSignOut,
}: SignedInPanelProps) {
  const { t } = useTranslation("auth");
  const displayName = session.displayName || session.username || "";
  const initial = displayName.slice(0, 1).toUpperCase();

  return (
    <div className={styles.view}>
      <div className={styles.brand} aria-label={BRAND_WORDMARK}>
        <span className={styles.brandMark} aria-hidden="true">{BRAND_MARK}</span>
        <span className={styles.brandWordmark}>{BRAND_WORDMARK}</span>
      </div>

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
          <Icon name="mdi:check-circle-outline" size={22} />
        </span>
      </div>

      <div className={styles.actions}>
        {destinations.map((destination) => (
          <Button
            key={destination.key}
            type="button"
            size="lg"
            radius="lg"
            block
            variant={destination.primary ? "primary" : "outline"}
            startIcon={<Icon name={destination.icon} size={18} />}
            className={styles.action}
            onClick={() => onNavigate(destination.href)}
          >
            {t(destination.labelKey)}
          </Button>
        ))}

        <Button
          type="button"
          size="lg"
          radius="lg"
          block
          variant="ghost"
          loading={signingOut}
          loadingText={t("signingOut")}
          startIcon={<Icon name="mdi:logout" size={18} />}
          className={styles.signOut}
          onClick={onSignOut}
        >
          {t("signOut")}
        </Button>
      </div>
    </div>
  );
}
