import { useTranslation } from "react-i18next";
import { BRAND_WORDMARK } from "../../../constants";
import styles from "./auth-branding.module.css";

export interface AuthBrandingProps {
  showSlogan?: boolean;
}

export function AuthBranding({ showSlogan = true }: AuthBrandingProps) {
  const { t } = useTranslation("auth");

  if (!showSlogan) {
    return null;
  }

  return (
    <section className={styles.slogan} aria-label={t("sloganLine1")}>
      <p className={styles.sloganKicker}>{BRAND_WORDMARK} · DIGITAL IDENTITY</p>
      <h1 className={styles.sloganTitle}>
        <span>{t("sloganLine1")}</span>
        <span>{t("sloganLine2")}</span>
      </h1>
    </section>
  );
}
