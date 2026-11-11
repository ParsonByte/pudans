import { useTranslation } from "react-i18next";
import { DropdownMenu, Icon, Tooltip, type PudansColorModePreference } from "@pudans/components";
import { useAppDispatch, useAppSelector } from "@web/store/hooks";
import { setLocale, setThemePreference } from "@web/store/app-slice";
import styles from "./auth-topbar.module.css";

const THEME_ORDER: readonly PudansColorModePreference[] = ["light", "dark", "system"];

const THEME_ICONS: Record<PudansColorModePreference, string> = {
  light: "mdi:white-balance-sunny",
  dark: "mdi:weather-night",
  system: "mdi:laptop",
};

export function AuthTopbar() {
  const { t, i18n } = useTranslation("auth");
  const dispatch = useAppDispatch();
  const themePreference = useAppSelector((state) => state.app.themePreference);

  const themeLabel = t(`theme${themePreference[0].toUpperCase()}${themePreference.slice(1)}`);
  const currentLanguage = i18n.language.startsWith("zh") ? "zh-CN" : "en-US";
  const currentLanguageLabel = currentLanguage === "zh-CN" ? t("languageZh") : t("languageEn");

  const cycleTheme = (): void => {
    const currentIndex = THEME_ORDER.indexOf(themePreference);
    dispatch(setThemePreference(THEME_ORDER[(currentIndex + 1) % THEME_ORDER.length]));
  };

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

        <Tooltip title={t("themeTooltip", { theme: themeLabel })} placement="bottom">
          <button
            type="button"
            className={styles.iconButton}
            aria-label={t("theme")}
            onClick={cycleTheme}
          >
            <Icon name={THEME_ICONS[themePreference]} size={18} />
          </button>
        </Tooltip>

        <DropdownMenu
          showChevron={false}
          triggerLabel={t("language")}
          triggerClassName={styles.iconButton}
          items={[
            {
              key: "zh-CN",
              label: t("languageZh"),
              icon:
                currentLanguage === "zh-CN" ? (
                  <Icon name="mdi:check-circle-outline" size={16} />
                ) : undefined,
              onSelect: () => {
                void changeLanguage("zh-CN");
              },
            },
            {
              key: "en-US",
              label: t("languageEn"),
              icon:
                currentLanguage === "en-US" ? (
                  <Icon name="mdi:check-circle-outline" size={16} />
                ) : undefined,
              onSelect: () => {
                void changeLanguage("en-US");
              },
            },
          ]}
          trigger={
            <Tooltip
              title={t("languageTooltip", { language: currentLanguageLabel })}
              placement="bottom"
            >
              <span className={styles.languageIcon}>
                <Icon name="mdi:translate" size={19} />
              </span>
            </Tooltip>
          }
        />
      </div>
    </header>
  );
}
