import { useTranslation } from "react-i18next";
import { Icon, LanguageSwitcher, ThemeSwitch } from "@pudans/components";
import { useAppDispatch } from "@web/store/hooks";
import { setLocale } from "@web/store/app-slice";
import styles from "./auth-topbar.module.css";

export function AuthTopbar() {
  const { t, i18n } = useTranslation("auth");
  const dispatch = useAppDispatch();
  const currentLanguage = i18n.language.startsWith("zh") ? "zh-CN" : "en-US";

  const changeLanguage = async (locale: string): Promise<void> => {
    dispatch(setLocale(locale));
    await i18n.changeLanguage(locale);
  };

  return (
    <header className={styles.nav}>
      <div className={styles.left}>
        <a className={styles.brand} href="/auth/" aria-label="PUDANS">
          <span className={styles.brandMark}>P</span>
          <span className={styles.brandText}>
            <strong>PUDANS</strong>
            <small>{t("brandSub")}</small>
          </span>
        </a>
      </div>

      <div className={styles.actions}>
        <a className={styles.helpLink} href="mailto:support@pudans.tolim.cn">
          <Icon name="mdi:help-circle-outline" size={17} />
          <span>{t("help")}</span>
        </a>

        <ThemeSwitch size="sm" aria-label={t("theme")} />

        <LanguageSwitcher
          compact
          ariaLabel={t("language")}
          languages={[
            { code: "zh-CN", label: t("languageZh") },
            { code: "en-US", label: t("languageEn") },
          ]}
          value={currentLanguage}
          onChange={(locale) => {
            void changeLanguage(locale);
          }}
        />
      </div>
    </header>
  );
}
