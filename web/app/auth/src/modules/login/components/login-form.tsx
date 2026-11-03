import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { clsx } from "clsx";
import { Button, Icon, Input, useToast } from "@pudans/components";
import { useLogin } from "../composables/use-login";
import FeishuIcon from "./feishu-icon";
import styles from "./login-form.module.css";

const FEISHU_AUTHORIZATION_URL =
  "/apis/auth.api.pudans.tolim.cn/v1alpha1/oauth2/authorization/feishu";

export function LoginForm() {
  const { t } = useTranslation("auth");
  const toast = useToast();
  const [activeTab, setActiveTab] = useState<"login" | "scan">("login");
  const {
    username,
    password,
    passwordVisible,
    loading,
    error,
    setError,
    setUsername,
    setPassword,
    togglePasswordVisibility,
    submit,
  } = useLogin();

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (!username.trim() || !password) {
      const message = t("errorRequired");
      setError(message);
      toast.error(message);
      return;
    }
    void submit(event);
  };

  return (
    <div className={styles.view}>
      <div className={styles.cardTop}>
        <span className={styles.cardEyebrow}>{t("welcome")}</span>
        <h2 className={styles.cardTitle}>{t("title")}</h2>
        <p className={styles.cardDescription}>{t("description")}</p>
      </div>

      <div className={styles.tabs} role="tablist" aria-label={t("tabLogin")}>
        <button
          type="button"
          role="tab"
          className={clsx(styles.tab, activeTab === "login" && styles.tabActive)}
          aria-selected={activeTab === "login"}
          onClick={() => setActiveTab("login")}
        >
          {t("tabLogin")}
        </button>
        <button
          type="button"
          role="tab"
          className={clsx(styles.tab, activeTab === "scan" && styles.tabActive)}
          aria-selected={activeTab === "scan"}
          onClick={() => setActiveTab("scan")}
        >
          {t("tabScan")}
        </button>
      </div>

      {activeTab === "scan" ? (
        <div className={styles.scanPanel} role="tabpanel">
          <span className={styles.scanIcon} aria-hidden="true">
            <Icon name="mdi:qrcode" size={46} />
          </span>
          <h3 className={styles.scanTitle}>{t("scanTitle")}</h3>
          <p className={styles.scanText}>{t("scanSoon")}</p>
          <p className={styles.scanHint}>{t("scanHint")}</p>
        </div>
      ) : (
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

          <div className={styles.divider}>{t("otherMethods")}</div>
          <div className={styles.providers}>
            <button
              type="button"
              className={styles.providerButton}
              disabled
              title={t("comingSoon")}
            >
              <Icon name="mdi:fingerprint" size={17} />
              {t("passkey")}
            </button>
            <a
              className={clsx(styles.providerButton, styles.providerLink)}
              href={FEISHU_AUTHORIZATION_URL}
            >
              <FeishuIcon size={17} />
              {t("feishu")}
            </a>
          </div>
        </form>
      )}
    </div>
  );
}