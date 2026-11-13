import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { BRAND_WORDMARK } from "../../../constants";
import styles from "./auth-footer.module.css";

export function AuthFooter() {
  const { t } = useTranslation("auth");
  const year = new Date().getFullYear();

  return (
    <footer className={styles.footer}>
      <span>{t("copyright", { year, brand: BRAND_WORDMARK })}</span>
      <Link to="/auth/legal?tab=terms">{t("terms")}</Link>
      <Link to="/auth/legal?tab=privacy">{t("privacy")}</Link>
      <Link to="/auth/legal?tab=confidentiality">{t("dataConfidentiality")}</Link>
    </footer>
  );
}
