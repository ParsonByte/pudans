import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { Button, Icon } from "@pudans/components";
import { BRAND_MARK, BRAND_WORDMARK } from "../../../../../constants";
import styles from "./signed-in-panel.module.css";

/** Visible confirmation after logout; never a blank screen or auth redirect. */
export function SignedOutPanel() {
  const { t } = useTranslation("auth");
  const navigate = useNavigate();

  return (
    <div className={styles.view}>
      <div className={styles.brand} aria-label={BRAND_WORDMARK}>
        <span className={styles.brandMark} aria-hidden="true">{BRAND_MARK}</span>
        <span className={styles.brandWordmark}>{BRAND_WORDMARK}</span>
      </div>

      <div className={styles.header}>
        <span className={styles.eyebrow}>{t("signedOutEyebrow")}</span>
        <h2 className={styles.title}>{t("signedOutTitle")}</h2>
        <p className={styles.description}>{t("signedOutDescription")}</p>
      </div>

      <div className={styles.statusIcon} aria-hidden="true">
        <Icon name="mdi:logout" size={28} />
      </div>

      <div className={styles.actions}>
        <Button
          type="button"
          size="lg"
          radius="lg"
          block
          variant="primary"
          startIcon={<Icon name="mdi:arrow-right" size={18} />}
          className={styles.action}
          onClick={() => navigate("/auth")}
        >
          {t("signInAgain")}
        </Button>
      </div>
    </div>
  );
}
