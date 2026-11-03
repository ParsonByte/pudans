import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import styles from "./auth-footer.module.css";

export function AuthFooter() {
  const { t } = useTranslation("auth");
  const year = new Date().getFullYear();

  return (
    <footer className={styles.footer}>
      <span>{t("copyright", { year })}</span>
      <Link to="/legal?tab=terms">{t("terms")}</Link>
      <Link to="/legal?tab=privacy">{t("privacy")}</Link>
      <Link to="/legal?tab=confidentiality">{t("dataConfidentiality")}</Link>
    </footer>
  );
}