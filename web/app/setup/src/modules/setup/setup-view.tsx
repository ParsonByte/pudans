import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Button,
  Card,
  DropdownMenu,
  Icon,
  Input,
  Tooltip,
  useToast,
  type PudansColorModePreference,
} from "@pudans/components";
import { useAppStore } from "@web/store/app-store";
import {
  apiClient,
  getErrorMessage,
  type SetupPayload,
} from "@pudans/api-client";
import { useSetupLocale } from "./composables/use-setup-locale";
import styles from "./setup-view.module.css";

type FieldErrors = Partial<
  Record<keyof SetupPayload | "confirmPassword", string>
>;

const defaultSiteUrl =
  import.meta.env.VITE_SITE_URL ??
  (typeof window !== "undefined" ? window.location.origin : "");

const THEME_ORDER: readonly PudansColorModePreference[] = [
  "light",
  "dark",
  "system",
];

const THEME_ICONS: Record<PudansColorModePreference, string> = {
  light: "mdi:white-balance-sunny",
  dark: "mdi:weather-night",
  system: "mdi:laptop",
};

export function SetupView() {
  const { t, i18n } = useTranslation("setup");
  const setupLocale = useSetupLocale();
  const toast = useToast();
  const themePreference = useAppStore((state) => state.themePreference);
  const setThemePreference = useAppStore((state) => state.setThemePreference);
  const setLocale = useAppStore((state) => state.setLocale);

  const themeLabel = t(`settings.theme${themePreference[0].toUpperCase()}${themePreference.slice(1)}`);
  const currentLanguage = i18n.language.startsWith("zh") ? "zh-CN" : "en-US";
  const currentLanguageLabel =
    currentLanguage === "zh-CN" ? t("settings.languageZh") : t("settings.languageEn");

  const cycleTheme = (): void => {
    const currentIndex = THEME_ORDER.indexOf(themePreference);
    setThemePreference(THEME_ORDER[(currentIndex + 1) % THEME_ORDER.length]);
  };

  const changeLanguage = async (locale: string): Promise<void> => {
    setLocale(locale);
    await i18n.changeLanguage(locale);
  };
  const [form, setForm] = useState<SetupPayload>({
    username: "",
    password: "",
    email: "",
    siteTitle: "pudans",
    siteUrl: defaultSiteUrl,
  });
  const [confirmPassword, setConfirmPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const passwordsMatch =
    form.password.length > 0 && form.password === confirmPassword;

  const setField = <K extends keyof SetupPayload>(
    key: K,
    value: SetupPayload[K],
  ): void => {
    setForm((current) => ({ ...current, [key]: value }));
    setFieldErrors((current) => ({
      ...current,
      [key]: undefined,
      ...(key === "password" ? { confirmPassword: undefined } : {}),
    }));
    setError(null);
  };

  const setConfirmation = (value: string): void => {
    setConfirmPassword(value);
    setFieldErrors((current) => ({ ...current, confirmPassword: undefined }));
    setError(null);
  };

  const validate = (): boolean => {
    const nextErrors: FieldErrors = {};
    if (!form.username.trim()) {
      nextErrors.username = t("required");
    } else if (
      form.username.length < 4 ||
      form.username.length > 63 ||
      !/^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$/.test(
        form.username,
      )
    ) {
      nextErrors.username = t("usernamePattern");
    }
    if (!form.email?.trim()) {
      nextErrors.email = t("required");
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
      nextErrors.email = t("emailInvalid");
    }
    if (form.password.length < 8) {
      nextErrors.password = t("passwordMin");
    } else if (!/^[A-Za-z0-9!@#$%^&*.?]+$/.test(form.password)) {
      nextErrors.password = t("passwordPattern");
    }
    if (!confirmPassword) {
      nextErrors.confirmPassword = t("confirmPasswordRequired");
    } else if (form.password !== confirmPassword) {
      nextErrors.confirmPassword = t("passwordMismatch");
    }
    if (!form.siteTitle.trim()) {
      nextErrors.siteTitle = t("required");
    }
    try {
      const url = new URL(form.siteUrl);
      if (url.protocol !== "http:" && url.protocol !== "https:") {
        nextErrors.siteUrl = t("invalidUrl");
      }
    } catch {
      nextErrors.siteUrl = t("invalidUrl");
    }
    setFieldErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> => {
    event.preventDefault();
    setError(null);
    if (!passwordsMatch || !validate()) {
      return;
    }

    setLoading(true);
    try {
      await apiClient.system.setup({
        username: form.username,
        password: form.password,
        email: form.email?.trim(),
        siteTitle: form.siteTitle,
        siteUrl: form.siteUrl,
        language: setupLocale,
      });
      setSuccess(true);
      toast.success(t("successDescription"));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  };

  const confirmationError =
    fieldErrors.confirmPassword ??
    (confirmPassword.length > 0 && !passwordsMatch
      ? t("passwordMismatch")
      : undefined);

  return (
    <main className={styles.page}>
      <div className={styles.settings}>
        <Tooltip
          title={t("settings.themeTooltip", { theme: themeLabel })}
          placement="bottom"
        >
          <button
            type="button"
            className={styles.settingButton}
            aria-label={t("settings.theme")}
            onClick={cycleTheme}
          >
            <Icon name={THEME_ICONS[themePreference]} size={20} />
          </button>
        </Tooltip>

        <DropdownMenu
          showChevron={false}
          triggerLabel={t("settings.language")}
          triggerClassName={styles.languageTrigger}
          items={[
            {
              key: "zh-CN",
              label: t("settings.languageZh"),
              icon:
                currentLanguage === "zh-CN" ? (
                  <Icon name="mdi:check-circle-outline" size={17} />
                ) : undefined,
              onSelect: () => {
                void changeLanguage("zh-CN");
              },
            },
            {
              key: "en-US",
              label: t("settings.languageEn"),
              icon:
                currentLanguage === "en-US" ? (
                  <Icon name="mdi:check-circle-outline" size={17} />
                ) : undefined,
              onSelect: () => {
                void changeLanguage("en-US");
              },
            },
          ]}
          trigger={
            <Tooltip
              title={t("settings.languageTooltip", { language: currentLanguageLabel })}
              placement="bottom"
            >
              <span className={styles.languageIcon}>
                <Icon name="mdi:translate" size={20} />
              </span>
            </Tooltip>
          }
        />
      </div>

      <header className={styles.brandBlock}>
        <div className={styles.brand} aria-label="PUDANS">
          PUDANS
        </div>
        <p className={styles.eyebrow}>{t("eyebrow")}</p>
      </header>

      <Card padding="lg" className={styles.card}>
        <div className={styles.cardHeader}>
          <h1 className={styles.title}>{t("title")}</h1>
          <p className={styles.description}>{t("description")}</p>
        </div>

        <form className={styles.form} noValidate onSubmit={handleSubmit}>
          <div className={styles.sectionLabel}>{t("cardTitle")}</div>

          <Input
            label={t("username")}
            placeholder={t("usernamePlaceholder")}
            value={form.username}
            autoComplete="username"
            autoCapitalize="off"
            spellCheck={false}
            disabled={loading || success}
            error={Boolean(fieldErrors.username)}
            helperText={fieldErrors.username}
            onChange={(event) => setField("username", event.target.value)}
          />

          <Input
            label={t("email")}
            placeholder={t("emailPlaceholder")}
            type="email"
            value={form.email ?? ""}
            autoComplete="email"
            disabled={loading || success}
            error={Boolean(fieldErrors.email)}
            helperText={fieldErrors.email}
            onChange={(event) => setField("email", event.target.value)}
          />

          <div className={styles.passwordGrid}>
            <Input
              label={t("password")}
              placeholder={t("passwordPlaceholder")}
              type="password"
              value={form.password}
              autoComplete="new-password"
              disabled={loading || success}
              error={Boolean(fieldErrors.password)}
              helperText={fieldErrors.password}
              onChange={(event) => setField("password", event.target.value)}
            />
            <Input
              label={t("confirmPassword")}
              placeholder={t("confirmPasswordPlaceholder")}
              type="password"
              value={confirmPassword}
              autoComplete="new-password"
              disabled={loading || success}
              error={Boolean(confirmationError)}
              helperText={confirmationError}
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </div>

          <Input
            label={t("siteTitle")}
            placeholder={t("siteTitlePlaceholder")}
            value={form.siteTitle}
            disabled={loading || success}
            error={Boolean(fieldErrors.siteTitle)}
            helperText={fieldErrors.siteTitle}
            onChange={(event) => setField("siteTitle", event.target.value)}
          />

          <Input
            label={t("siteUrl")}
            placeholder={t("siteUrlPlaceholder")}
            type="url"
            value={form.siteUrl}
            disabled={loading || success}
            error={Boolean(fieldErrors.siteUrl)}
            helperText={fieldErrors.siteUrl}
            onChange={(event) => setField("siteUrl", event.target.value)}
          />

          {error ? (
            <Alert severity="error" title={t("errorTitle")}>
              {error}
            </Alert>
          ) : null}

          {success ? (
            <Alert
              severity="success"
              title={t("successTitle")}
              action={
                <a className={styles.continueLink} href="/auth/">
                  {t("continue")}
                </a>
              }
            >
              {t("successDescription")}
            </Alert>
          ) : null}

          <Button
            type="submit"
            size="lg"
            radius="lg"
            block
            className={styles.submit}
            loading={loading}
            loadingText={t("submitting")}
            disabled={success || !passwordsMatch}
          >
            {t("submit")}
          </Button>

          <p className={styles.footer}>{t("footer")}</p>
        </form>
      </Card>
    </main>
  );
}