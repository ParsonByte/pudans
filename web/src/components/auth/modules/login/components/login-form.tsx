import { type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { Button, Icon, Input } from "@pudans/components";
import { useLogin } from "../composables/use-login";
import styles from "./login-form.module.css";

export function LoginForm() {
  const { t } = useTranslation("auth");
  const {
    username,
    password,
    passwordVisible,
    loading,
    providers,
    setupRequired,
    error,
    setUsername,
    setPassword,
    togglePasswordVisibility,
    submit,
  } = useLogin();

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    void submit(event);
  };

  if (setupRequired) {
    return (
      <div className={styles.view}>
        <div className={styles.cardTop}>
          <span className={styles.cardEyebrow}>{t("brandSub")}</span>
          <h2 className={styles.cardTitle}>{t("setupRequiredTitle")}</h2>
          <p className={styles.cardDescription}>{t("setupRequiredDescription")}</p>
        </div>
        <div className={styles.setupPanel} role="status">
          <span className={styles.setupIcon} aria-hidden="true">
            <Icon name="mdi:shield-alert-outline" size={34} />
          </span>
          <p className={styles.setupText}>{t("setupRequiredDescription")}</p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.view}>
      <div className={styles.cardTop}>
        <span className={styles.cardEyebrow}>{t("welcome")}</span>
        <h2 className={styles.cardTitle}>{t("title")}</h2>
        <p className={styles.cardDescription}>{t("description")}</p>
      </div>

      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <Input
          className={styles.input}
          label={t("username")}
          placeholder={t("usernamePlaceholder")}
          value={username}
          autoComplete="username"
          autoCapitalize="off"
          spellCheck={false}
          autoFocus
          startIcon={<Icon name="mdi:account-outline" size={18} />}
          onChange={(event) => setUsername(event.target.value)}
        />

        <Input
          className={styles.input}
          label={t("password")}
          placeholder={t("passwordPlaceholder")}
          type={passwordVisible ? "text" : "password"}
          value={password}
          autoComplete="current-password"
          error={Boolean(error)}
          startIcon={<Icon name="mdi:lock-outline" size={18} />}
          endIcon={
            <button
              type="button"
              className={styles.visibilityButton}
              aria-label={passwordVisible ? t("hidePassword") : t("showPassword")}
              onClick={togglePasswordVisibility}
            >
              <Icon
                name={passwordVisible ? "mdi:eye-off-outline" : "mdi:eye-outline"}
                size={18}
              />
            </button>
          }
          onChange={(event) => setPassword(event.target.value)}
        />

        <div className={styles.formMeta}>
          <Link className={styles.forgot} to="/recovery">
            {t("forgot")}
          </Link>
        </div>

        <div className={styles.hints}>
          <span>
            <Icon name="mdi:check-circle-outline" size={14} />
            {t("secureHint")}
          </span>
          <span>
            <Icon name="mdi:check-circle-outline" size={14} />
            {t("deviceHint")}
          </span>
        </div>

        <Button
          type="submit"
          size="lg"
          radius="lg"
          block
          loading={loading}
          loadingText={t("submitting")}
          endIcon={<Icon name="mdi:arrow-right" size={18} />}
          className={styles.submit}
        >
          {t("submit")}
        </Button>

        <p className={styles.agreement}>
          {t("agreementPrefix")}
          <Link to="/legal">{t("agreementTerms")}</Link>
        </p>

        {providers.length > 0 ? (
          <>
            <div className={styles.divider}>{t("otherMethods")}</div>
            <div className={styles.providers}>
              {providers.map((provider) => (
                <a
                  key={provider.name}
                  className={styles.providerButton}
                  href={provider.authenticationUrl}
                >
                  {provider.logo ? (
                    <img className={styles.providerLogo} src={provider.logo} alt="" />
                  ) : (
                    <Icon name="mdi:login-variant" size={17} />
                  )}
                  {provider.displayName}
                </a>
              ))}
            </div>
          </>
        ) : null}
      </form>
    </div>
  );
}
